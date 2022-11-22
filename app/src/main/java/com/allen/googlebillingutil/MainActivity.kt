package com.allen.googlebillingutil

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.allen.billing.GoogleBillingUtil
import com.allen.billing.OnGoogleBillingListener
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var googleBillingUtil: GoogleBillingUtil

    private val logBuffer = StringBuffer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        GoogleBillingUtil.isDebug(true)

        GoogleBillingUtil.setSkus(
            arrayOf("product1"),
            arrayOf("subs_1")
        )
        GoogleBillingUtil.setIsAutoAcknowledgePurchase(true)//设置自动确认购买
        googleBillingUtil = GoogleBillingUtil.getInstance()
            .addOnGoogleBillingListener(this, OnMyGoogleBillingListener())
            .build(this,this)
        findViewById<Button>(R.id.btNext).setOnClickListener {
            NextActivity.toActivity(this)
        }
        findViewById<TextView>(R.id.tvLogClean).setOnClickListener {
            findViewById<TextView>(R.id.tvLog).text = ""
        }
        findViewById<ImageView>(R.id.ivHelp).setOnClickListener {
            val intent = Intent()
            val uri = Uri.parse("https://gitee.com/yedong/GoogleBilling")
            intent.action = Intent.ACTION_VIEW
            intent.data = uri
            startActivity(intent)
        }
    }

    private inner class OnMyGoogleBillingListener : OnGoogleBillingListener() {
        //内购服务初始化成功
        override fun onSetupSuccess(isSelf: Boolean) {
            log("内购服务初始化完成")
            checkSubs()
        }

        override fun onQuerySkuSuccess(
            skuType: String,
            list: MutableList<SkuDetails>,
            isSelf: Boolean
        ) {
            if (!isSelf) return
            val tempBuffer = StringBuffer()
            tempBuffer.append("查询商品信息成功($skuType):\n")
            for ((i, skuDetails) in list.withIndex()) {
                val details = String.format(
                    Locale.getDefault(), "%s , %s",
                    skuDetails.sku, skuDetails.price
                )
                tempBuffer.append(details)
                if (i != list.size - 1) {
                    tempBuffer.append("\n")
                }
            }
            log(tempBuffer.toString())
        }

        override fun onRecheck(
            productType: String, purchase: Purchase, isEnd: Boolean, isSelf: Boolean
        ): Boolean {
            val tempBuffer = StringBuffer()
            tempBuffer.append("检测到未处理的订单($productType):${purchase.products[0]}(${getPurchaseStateTxt(purchase.purchaseState)})")
            log(tempBuffer.toString())
            if (purchase.products[0] == "noads") {
                //不要自动消耗
                return false
            }
            return true
        }

        override fun onPurchaseSuccess(purchase: Purchase, isSelf: Boolean): Boolean {
            val tempBuffer = StringBuffer()
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                tempBuffer.append("购买成功:")
            } else {
                tempBuffer.append("暂未支付:")
            }
            val details = String.format(Locale.getDefault(), "%s \n", purchase.products[0])
            tempBuffer.append(details)
            log(tempBuffer.toString())
            return true //自动消耗(只有当isSelf为true,并且支付状态为PURCHASED时，该值才会生效)
        }

        override fun onConsumeSuccess(purchaseToken: String, isSelf: Boolean) {
            log("消耗商品成功:$purchaseToken")
        }

        override fun onAcknowledgePurchaseSuccess(isSelf: Boolean) {
            log("确认购买商品成功")
        }

        override fun onFail(
            tag: GoogleBillingUtil.GoogleBillingListenerTag,
            responseCode: Int,
            isSelf: Boolean
        ) {
            log("操作失败:tag=${tag.name},responseCode=$responseCode")
        }

        override fun onError(tag: GoogleBillingUtil.GoogleBillingListenerTag, isSelf: Boolean) {
            log("发生错误:tag=${tag.name}")
        }

        override fun onSubsCountListener(count: Int) {
            when (count) {
                0 -> {
                    //不具备有效订阅
                    log("有效订阅数:0(无有效订阅)")
                }
                -1 -> {
                    //查询失败
                    log("有效订阅数:-1(查询失败)")
                }
                else -> {
                    //具有有效订阅
                    log("有效订阅数:$count(具备有效订阅)")
                }
            }
        }

        private fun getPurchaseStateTxt(@Purchase.PurchaseState state: Int): String {
            return when (state) {
                Purchase.PurchaseState.PURCHASED -> "PURCHASED"
                Purchase.PurchaseState.PENDING -> "PENDING"
                Purchase.PurchaseState.UNSPECIFIED_STATE -> "UNSPECIFIED_STATE"
                else -> "未知状态"
            }
        }
    }

    /**
     * 检查是否有有效订阅
     */
    private fun checkSubs() {
        googleBillingUtil.getPurchasesSizeSubs(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        googleBillingUtil.onDestroy(this)
        //退出程序的时候可以调用(实验性)
        GoogleBillingUtil.endConnection()
    }

    private fun log(msg: String) {
        val log = String.format(Locale.getDefault(), "%s\n%s\n\n", getTime(), msg)
        logBuffer.append(log)
        findViewById<TextView>(R.id.tvLog).text = logBuffer.toString()
    }

    private fun getTime(): String {
        val dt = Date()
        val sdf = DateFormat.getInstance() as SimpleDateFormat
        sdf.applyPattern("HH:mm:ss.SSS")
        return sdf.format(dt)
    }
}
