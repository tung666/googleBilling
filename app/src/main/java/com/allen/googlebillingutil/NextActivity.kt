package com.allen.googlebillingutil

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.allen.billing.GoogleBillingUtil
import com.allen.billing.OnGoogleBillingListener

class NextActivity : AppCompatActivity() {


    private lateinit var googleBillingUtil: GoogleBillingUtil

    companion object {
        fun toActivity(activity: AppCompatActivity) {
            val intent = Intent(activity, NextActivity::class.java)
            activity.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.next_activity_layout)
        googleBillingUtil = GoogleBillingUtil.getInstance()
            .addOnGoogleBillingListener(this, OnMyGoogleBillingListener())
            .build(this, this)
        findViewById<TextView>(R.id.tvBack).setOnClickListener {
            this.finish()
        }
        findViewById<Button>(R.id.btInapp).setOnClickListener {
            googleBillingUtil.purchaseInApp(
                this@NextActivity,
                this@NextActivity,
                googleBillingUtil.getInAppSkuByPosition(0)
            )
        }
        findViewById<Button>(R.id.btSubs).setOnClickListener {
            googleBillingUtil.purchaseSubs(
                this@NextActivity,
                this@NextActivity,
                googleBillingUtil.getSubsSkuByPosition(0)
            )
        }
    }

    private inner class OnMyGoogleBillingListener : OnGoogleBillingListener() {

        override fun onQuerySkuSuccess(
            skuType: String, list: MutableList<SkuDetails>, isSelf: Boolean
        ) {
            if (skuType == GoogleBillingUtil.BILLING_TYPE_INAPP) {
                //内购商品
                if (list.size > 0) {
                    findViewById<Button>(R.id.btInapp).text =
                        String.format("发起内购:%s", list[0].price)
                }
            } else if (skuType == GoogleBillingUtil.BILLING_TYPE_SUBS) {
                //订阅商品
                if (list.size > 0) {
                    findViewById<Button>(R.id.btSubs).text = String.format("发起订阅:%s", list[0].price)
                }
            }
        }

        override fun onQueryProductSuccess(
            productType: String, list: MutableList<ProductDetails>, isSelf: Boolean
        ) {
            if (productType == GoogleBillingUtil.BILLING_TYPE_INAPP) {
                //内购商品
                if (list.size > 0) {
                    findViewById<Button>(R.id.btInapp).text =
                        String.format("发起内购:%s", list[0].name)
                }
            } else if (productType == GoogleBillingUtil.BILLING_TYPE_SUBS) {
                //订阅商品
                if (list.size > 0) {
                    findViewById<Button>(R.id.btSubs).text = String.format("发起订阅:%s", list[0].name)
                }
            }
        }

        override fun onPurchaseSuccess(purchase: Purchase, isSelf: Boolean): Boolean {
            val sku = purchase.products[0]
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                val skuType = googleBillingUtil.getSkuType(sku)
                if (skuType == GoogleBillingUtil.BILLING_TYPE_INAPP) {
                    Toast.makeText(this@NextActivity, "内购成功:$sku", Toast.LENGTH_LONG).show()
                } else if (skuType == GoogleBillingUtil.BILLING_TYPE_SUBS) {
                    Toast.makeText(this@NextActivity, "订阅成功:$sku", Toast.LENGTH_LONG).show()
                }
            } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                Toast.makeText(this@NextActivity, "待处理的订单:$sku", Toast.LENGTH_LONG).show()
            }
            if (sku == "noads") {
                //不进行消耗
                return false
            }
            return true
        }

        override fun onFail(
            tag: GoogleBillingUtil.GoogleBillingListenerTag,
            responseCode: Int, isSelf: Boolean
        ) {
            super.onFail(tag, responseCode, isSelf)
            if (responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                Toast.makeText(this@NextActivity, "user canceled", Toast.LENGTH_LONG).show()

            }
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        googleBillingUtil.onDestroy(this)
    }

}