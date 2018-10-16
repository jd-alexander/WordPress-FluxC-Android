package org.wordpress.android.fluxc.example

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.util.DiffUtil
import android.support.v7.util.DiffUtil.DiffResult
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.RecyclerView.ViewHolder
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import dagger.android.AndroidInjection
import kotlinx.android.synthetic.main.activity_woo_order_list.*
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.Main
import kotlinx.coroutines.experimental.isActive
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.withContext
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.generated.WCOrderActionBuilder
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.WCOrderListDescriptor
import org.wordpress.android.fluxc.model.WCOrderModel
import org.wordpress.android.fluxc.model.list.Either
import org.wordpress.android.fluxc.model.list.Either.Left
import org.wordpress.android.fluxc.model.list.Either.Right
import org.wordpress.android.fluxc.model.list.ListDescriptor
import org.wordpress.android.fluxc.model.list.ListItemDataSource
import org.wordpress.android.fluxc.model.list.ListItemModel
import org.wordpress.android.fluxc.model.list.SectionedListManager
import org.wordpress.android.fluxc.store.ListStore
import org.wordpress.android.fluxc.store.ListStore.OnListChanged
import org.wordpress.android.fluxc.store.ListStore.OnListItemsChanged
import org.wordpress.android.fluxc.store.SiteStore
import org.wordpress.android.fluxc.store.WCOrderStore
import org.wordpress.android.fluxc.store.WCOrderStore.FetchOrderListPayload
import org.wordpress.android.fluxc.store.WCOrderStore.RemoteOrderPayload
import org.wordpress.android.fluxc.store.WooCommerceStore
import javax.inject.Inject

private const val LOCAL_SITE_ID = "LOCAL_SITE_ID"
private const val SECTION_VIEW_TYPE = 0
private const val ORDER_VIEW_TYPE = 1

class WooOrderListActivity : AppCompatActivity() {
    companion object {
        fun newInstance(context: Context, localSiteId: Int): Intent {
            val intent = Intent(context, WooOrderListActivity::class.java)
            intent.putExtra(LOCAL_SITE_ID, localSiteId)
            return intent
        }
    }

    @Inject internal lateinit var dispatcher: Dispatcher
    @Inject internal lateinit var wooCommerceStore: WooCommerceStore
    @Inject internal lateinit var listStore: ListStore
    @Inject internal lateinit var siteStore: SiteStore
    @Inject internal lateinit var wcOrderStore: WCOrderStore

    private lateinit var listDescriptor: WCOrderListDescriptor
    private lateinit var site: SiteModel
    private lateinit var listManager: SectionedListManager<String, WCOrderModel>

    private var listAdapter: OrderListAdapter? = null
    private var refreshListDataJob: Job? = null
    private val filterStatusOptions: Array<String> by lazy {
        resources.getStringArray(R.array.order_filter_options)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_woo_order_list)

        dispatcher.register(this)
        site = siteStore.getSiteByLocalId(intent.getIntExtra(LOCAL_SITE_ID, 0))

        // delete all local orders from the database
        dispatcher.dispatch(WCOrderActionBuilder.newRemoveAllOrdersAction())

        listDescriptor = WCOrderListDescriptor(site)

        runBlocking { listManager = getListDataFromStore(listDescriptor) }
        setupViews()

        listManager.refresh()
    }

    override fun onStop() {
        super.onStop()
        dispatcher.unregister(this)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.order_list_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        item?.let {
            if (it.itemId == R.id.menu_order_filter) {
                showFilterMenu()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupViews() {
        recycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        recycler.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        listAdapter = OrderListAdapter(this, listManager)
        recycler.adapter = listAdapter

        swipeToRefresh.setOnRefreshListener {
            refreshListManagerFromStore(listDescriptor, true)
        }
    }

    private fun refreshListManagerFromStore(listDescriptor: ListDescriptor, fetchAfter: Boolean) {
        refreshListDataJob?.cancel()
        refreshListDataJob = GlobalScope.launch(Dispatchers.Main) {
            val listManager = withContext(Dispatchers.Default) { getListDataFromStore(listDescriptor) }
            if (isActive && this@WooOrderListActivity.listDescriptor == listDescriptor) {
                val diffResult = withContext(Dispatchers.Default) {
                    DiffUtil.calculateDiff(DiffCallback(this@WooOrderListActivity.listManager, listManager))
                }
                if (isActive && this@WooOrderListActivity.listDescriptor == listDescriptor) {
                    updateListManager(listManager, diffResult)
                    if (fetchAfter) {
                        listManager.refresh()
                    }
                }
            }
        }
    }

    private fun updateListManager(listManager: SectionedListManager<String, WCOrderModel>, diffResult: DiffResult) {
        this.listManager = listManager
        swipeToRefresh.isRefreshing = listManager.isFetchingFirstPage
        loadingMoreProgressBar.visibility = if (listManager.isLoadingMore) View.VISIBLE else View.GONE
        listAdapter?.setListManager(listManager, diffResult)
    }

    private suspend fun getListDataFromStore(listDescriptor: ListDescriptor): SectionedListManager<String, WCOrderModel> {
        val dataSource = object : ListItemDataSource<WCOrderModel> {
            override fun fetchItem(listDescriptor: ListDescriptor, remoteItemId: Long) {
                val orderToFetch = WCOrderModel().apply {
                    this.remoteOrderId = remoteItemId
                    this.localSiteId = site.id
                }
                val payload = RemoteOrderPayload(orderToFetch, site)
                dispatcher.dispatch(WCOrderActionBuilder.newFetchSingleOrderAction(payload))
            }

            override fun fetchList(listDescriptor: ListDescriptor, offset: Int) {
                if (listDescriptor is WCOrderListDescriptor) {
                    val fetchOrderListPayload = FetchOrderListPayload(listDescriptor, offset)
                    dispatcher.dispatch(WCOrderActionBuilder.newFetchOrderListAction(fetchOrderListPayload))
                }
            }

            override fun getItems(listDescriptor: ListDescriptor, remoteItemIds: List<Long>): Map<Long, WCOrderModel> {
                return wcOrderStore.getOrdersByRemoteOrderIds(remoteItemIds, site)
            }
        }
        return listStore.getSectionedListManager(listDescriptor, dataSource, { listItems ->
            val ordersAsRight = listItems.map { Right<String, ListItemModel>(it) }
            ordersAsRight.asSequence().chunked(10)
                    .fold(listOf<Either<String, ListItemModel>>()) { acc, list ->
                        acc.asSequence().plus(listOf(Left("Section Title"))).plus(list).toList()
            }
        })
    }

    // region Eventbus listeners
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    @Suppress("unused")
    fun onListChanged(event: OnListChanged) {
        if (!event.listDescriptors.contains(listDescriptor)) {
            return
        }
        refreshListManagerFromStore(listDescriptor, false)
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    @Suppress("unused")
    fun onListItemsChanged(event: OnListItemsChanged) {
        if (listDescriptor.typeIdentifier != event.type) {
            return
        }
        refreshListManagerFromStore(listDescriptor, false)
    }
    // endregion

    // region Filtering
    private fun showFilterMenu() {
        val builder = AlertDialog.Builder(this)
        builder.apply {
            setTitle("Filter by Order Status")
            setItems(filterStatusOptions, DialogInterface.OnClickListener { _, which ->
                applyFilter(which)
            })
            setNegativeButton("Clear Filter", DialogInterface.OnClickListener { _, _ ->
                clearFilter()
            })
        }
        builder.create().show()
    }

    private fun clearFilter() {
        listDescriptor = WCOrderListDescriptor(site)
        refreshListManagerFromStore(listDescriptor, true)
    }

    private fun applyFilter(selectedIndex: Int) {
        val selectedFilter = filterStatusOptions[selectedIndex]
        listDescriptor = WCOrderListDescriptor(site, selectedFilter)
        refreshListManagerFromStore(listDescriptor, true)
    }
    // endregion

    // region Classes
    class OrderListAdapter(
        context: Context,
        private var listManager: SectionedListManager<String, WCOrderModel>
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val layoutInflater = LayoutInflater.from(context)

        fun setListManager(listManager: SectionedListManager<String, WCOrderModel>, diffResult: DiffResult) {
            this.listManager = listManager
            diffResult.dispatchUpdatesTo(this)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = when (viewType) {
            SECTION_VIEW_TYPE -> {
                val view = layoutInflater.inflate(R.layout.order_list_section, parent, false)
                SectionViewHolder(view)
            }
            else -> {
                val view = layoutInflater.inflate(R.layout.order_list_row, parent, false)
                OrderViewHolder(view)
            }
        }

        override fun getItemCount() = listManager.size

        override fun getItemViewType(position: Int): Int =
                if (listManager.isSection(position)) SECTION_VIEW_TYPE else ORDER_VIEW_TYPE

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val itemAtPosition = listManager.getItem(position)
            if (itemAtPosition?.isLeft == true) {
                val sectionHolder = holder as SectionViewHolder
                sectionHolder.title.text = (itemAtPosition as Left).value
                return
            }
            val orderModel = (itemAtPosition as Right?)?.value
            val orderHolder = holder as OrderViewHolder
            orderModel?.let {
                val id = it.remoteOrderId
                val customerName = it.getCustomerName()
                val orderStatus = it.status.orEmpty()

                orderHolder.orderIdView.text = id.toString()
                orderHolder.customerView.text = customerName
                orderHolder.statusView.text = orderStatus

                hideLoadingView(orderHolder.loadingView)
            } ?: showLoadingView(orderHolder.loadingView)
        }

        private fun showLoadingView(loadingView: ViewGroup) {
            loadingView.visibility = View.VISIBLE
        }

        private fun hideLoadingView(loadingView: ViewGroup) {
            loadingView.visibility = View.GONE
        }

        private class OrderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val orderIdView: TextView = itemView.findViewById(R.id.order_id)
            val customerView: TextView = itemView.findViewById(R.id.cust_name)
            val statusView: TextView = itemView.findViewById(R.id.order_status)
            val loadingView: ViewGroup = itemView.findViewById(R.id.order_loading)
        }

        private class SectionViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
            val title: TextView = itemView.findViewById(R.id.section_title)
        }
    }

    class DiffCallback(
        private val old: SectionedListManager<String, WCOrderModel>,
        private val new: SectionedListManager<String, WCOrderModel>
    ) : DiffUtil.Callback() {
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return SectionedListManager.areItemsTheSame(new, old, newItemPosition, oldItemPosition)
        }

        override fun getOldListSize() = old.size

        override fun getNewListSize() = new.size

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = old.getItem(oldItemPosition, false, false)
            val newItem = new.getItem(newItemPosition, false, false)
            if (oldItem == null && newItem == null) {
                return true
            }
            if (oldItem == null || newItem == null) {
                return false
            }
            if (oldItem is Left && newItem is Left) {
                return oldItem == newItem
            }
            if (oldItem is Left || newItem is Left) {
                return false
            }
            val oldOrder = (oldItem as Right).value
            val newOrder = (newItem as Right).value
            return oldOrder.getCustomerName() == newOrder.getCustomerName() && oldOrder.status == newOrder.status
        }
    }
    // endregion
}

// region Extension Functions
fun WCOrderModel.getCustomerName(): String {
    return "${this.billingFirstName} ${this.billingLastName}"
}
// endregion
