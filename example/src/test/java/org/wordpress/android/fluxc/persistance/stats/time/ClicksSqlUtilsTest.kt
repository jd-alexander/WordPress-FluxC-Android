package org.wordpress.android.fluxc.persistance.stats.time

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.network.rest.wpcom.stats.time.ClicksRestClient.ClicksResponse
import org.wordpress.android.fluxc.network.rest.wpcom.stats.time.StatsUtils
import org.wordpress.android.fluxc.network.utils.StatsGranularity.DAYS
import org.wordpress.android.fluxc.network.utils.StatsGranularity.MONTHS
import org.wordpress.android.fluxc.network.utils.StatsGranularity.WEEKS
import org.wordpress.android.fluxc.network.utils.StatsGranularity.YEARS
import org.wordpress.android.fluxc.persistence.StatsSqlUtils
import org.wordpress.android.fluxc.persistence.StatsSqlUtils.BlockType.CLICKS
import org.wordpress.android.fluxc.persistence.StatsSqlUtils.StatsType.DAY
import org.wordpress.android.fluxc.persistence.StatsSqlUtils.StatsType.MONTH
import org.wordpress.android.fluxc.persistence.StatsSqlUtils.StatsType.WEEK
import org.wordpress.android.fluxc.persistence.StatsSqlUtils.StatsType.YEAR
import org.wordpress.android.fluxc.persistence.TimeStatsSqlUtils
import org.wordpress.android.fluxc.store.stats.time.CLICKS_RESPONSE
import java.util.Date
import kotlin.test.assertEquals

private val DATE = Date(0)
private const val DATE_VALUE = "2018-10-10"

@RunWith(MockitoJUnitRunner::class)
class ClicksSqlUtilsTest {
    @Mock lateinit var statsSqlUtils: StatsSqlUtils
    @Mock lateinit var site: SiteModel
    @Mock lateinit var statsUtils: StatsUtils
    private lateinit var timeStatsSqlUtils: TimeStatsSqlUtils
    private val mappedTypes = mapOf(DAY to DAYS, WEEK to WEEKS, MONTH to MONTHS, YEAR to YEARS)

    @Before
    fun setUp() {
        timeStatsSqlUtils = TimeStatsSqlUtils(statsSqlUtils, statsUtils)
        whenever(statsUtils.getFormattedDate(eq(site), eq(DATE))).thenReturn(DATE_VALUE)
    }

    @Test
    fun `returns data from stats utils`() {
        mappedTypes.forEach { statsType, dbGranularity ->

            whenever(statsSqlUtils.select(site, CLICKS, statsType, ClicksResponse::class.java, DATE_VALUE))
                    .thenReturn(
                            CLICKS_RESPONSE
                    )

            val result = timeStatsSqlUtils.selectClicks(site, dbGranularity, DATE)

            assertEquals(result, CLICKS_RESPONSE)
        }
    }

    @Test
    fun `inserts data to stats utils`() {
        mappedTypes.forEach { statsType, dbGranularity ->
            timeStatsSqlUtils.insert(site, CLICKS_RESPONSE, dbGranularity, DATE)

            verify(statsSqlUtils).insert(site, CLICKS, statsType, CLICKS_RESPONSE, DATE_VALUE)
        }
    }
}
