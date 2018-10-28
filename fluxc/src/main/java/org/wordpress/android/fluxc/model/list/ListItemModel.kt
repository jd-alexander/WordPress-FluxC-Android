package org.wordpress.android.fluxc.model.list

import com.yarolegovich.wellsql.core.Identifiable
import com.yarolegovich.wellsql.core.annotation.Column
import com.yarolegovich.wellsql.core.annotation.PrimaryKey
import com.yarolegovich.wellsql.core.annotation.RawConstraints
import com.yarolegovich.wellsql.core.annotation.Table

@Table
@RawConstraints(
        "FOREIGN KEY(LIST_ID) REFERENCES ListModel(_id) ON DELETE CASCADE"
)
class ListItemModel(@PrimaryKey @Column private var id: Int = 0) : Identifiable {
    constructor(listId: Int, remoteItemId: Long): this() {
        this.listId = listId
        this.remoteItemId = remoteItemId
    }
    constructor(listId: Int, markerId: Int): this() {
        this.listId = listId
        this.markerId = markerId
    }

    @Column var listId: Int = 0
    @Column var remoteItemId: Long = 0
    @Column var markerId: Int = 0

    override fun getId(): Int = id

    override fun setId(id: Int) {
        this.id = id
    }
}
