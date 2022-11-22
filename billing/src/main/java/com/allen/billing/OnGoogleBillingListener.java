package com.allen.billing;

import androidx.annotation.NonNull;

import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchaseHistoryRecord;
import com.android.billingclient.api.SkuDetails;

import java.util.List;

/**
*
 */
public class OnGoogleBillingListener {

    @SuppressWarnings("WeakerAccess")
    public String tag = null;

    /**
     * 查询Sku成功，不支持 BillingClient.FeatureType.PRODUCT_DETAILS 的查询商品回调方法
     * @param productType 内购或者订阅
     * @param list 商品列表
     * @param isSelf 是否是当前页面的结果
     */
    public void onQuerySkuSuccess(@NonNull String productType, @NonNull List<SkuDetails> list, boolean isSelf){}

    /**
     * 查询Product成功，支持 BillingClient.FeatureType.PRODUCT_DETAILS 的查询商品回调方法
     * @param productType 内购或者订阅
     * @param list 商品列表
     * @param isSelf 是否是当前页面的结果
     */
    public void onQueryProductSuccess(@NonNull String productType, @NonNull List<ProductDetails> list, boolean isSelf){}

    /**
     * 购买成功
     * @param purchase 商品
     * @param isSelf 是否是当前页面的结果
     *
     * @return 是否消耗，只有当isSelf为true,并且支付状态为{@link com.android.billingclient.api.Purchase.PurchaseState.PURCHASED}时，该值才会生效。
     */
    public boolean onPurchaseSuccess(@NonNull Purchase purchase, boolean isSelf){return true;}

    /**
     * 初始化成功
     * @param isSelf 是否是当前页面的结果
     */
    public void onSetupSuccess(boolean isSelf){}

    /**
     * 每次启动重新检查订单，返回有效的订单
     *
     * @param productType 内购或者订阅
     * @param purchase    商品
     * @param isSelf  是否是当前页面的结果
     *
     * @return 是否自动消耗，只有当isSelf为true,并且支付状态为{@link com.android.billingclient.api.Purchase.PurchaseState.PURCHASED}时，该值才会生效。
     */
    public boolean onRecheck(@NonNull String productType, @NonNull Purchase purchase, boolean isEnd, boolean isSelf) {
        return false;
    }

    /**
     * 链接断开
     */
    @SuppressWarnings("WeakerAccess")
    public void onBillingServiceDisconnected(){ }

    /**
     * 消耗成功
     * @param purchaseToken token
     * @param isSelf 是否是当前页面的结果
     */
    public void onConsumeSuccess(@NonNull String purchaseToken,boolean isSelf){}


    /**
     * 确认购买成功
     * @param isSelf 是否是当前页面的结果
     */
    public void onAcknowledgePurchaseSuccess(boolean isSelf){}

    /**
     * 失败回调
     * @param tag {@link GoogleBillingUtil.GoogleBillingListenerTag}
     * @param responseCode 返回码{https://developer.android.com/google/play/billing/billing_reference}
     * @param isSelf 是否是当前页面的结果
     */
    public void onFail(@NonNull GoogleBillingUtil.GoogleBillingListenerTag tag, int responseCode, boolean isSelf){}

    /**
     * google组件初始化失败等等。
     * @param tag {@link GoogleBillingUtil.GoogleBillingListenerTag}
     * @param isSelf 是否是当前页面的结果
     */
    public void onError(@NonNull GoogleBillingUtil.GoogleBillingListenerTag tag, boolean isSelf){}


    /**
     * 获取历史订单-无论是否还有效
     * @param purchaseList 商品历史列表
     */
    public void onQueryHistory(@NonNull List<PurchaseHistoryRecord> purchaseList){

    }

    /**
     * 订阅数量回调
     * @param count
     */
    public void onSubsCountListener(int count){

    }
}
