package com.allen.billing;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchaseHistoryRecord;
import com.android.billingclient.api.PurchaseHistoryResponseListener;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchaseHistoryParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 支付工具类
 */

@SuppressWarnings({"WeakerAccess", "unused", "UnusedReturnValue"})
public class GoogleBillingUtil {

    private static final String TAG = "Billing-" + BuildConfig.VERSION_NAME;
    private static boolean IS_DEBUG = false;
    private static String[] inAppSKUS = new String[]{};//内购ID,必填，注意！如果用不着的请去掉多余的""
    private static String[] subsSKUS = new String[]{};//订阅ID,必填，注意！如果用不着的请去掉多余的""

    public static final String BILLING_TYPE_INAPP = BillingClient.ProductType.INAPP;//内购
    public static final String BILLING_TYPE_SUBS = BillingClient.ProductType.SUBS;//订阅

    private static BillingClient mBillingClient;
    private static BillingClient.Builder builder;
    private CopyOnWriteArrayList<OnGoogleBillingListener> onGoogleBillingListenerList = new CopyOnWriteArrayList<>();
    private MyPurchasesUpdatedListener purchasesUpdatedListener = new MyPurchasesUpdatedListener();

    private static boolean isAutoAcknowledgePurchase = true;
    private static Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final GoogleBillingUtil mGoogleBillingUtil = new GoogleBillingUtil();

    private GoogleBillingUtil() {

    }

    //region===================================初始化google应用内购买服务=================================

    /**
     * 设置skus
     *
     * @param inAppSKUS 内购id
     * @param subsSKUS  订阅id
     */
    public static void setSkus(@Nullable String[] inAppSKUS, @Nullable String[] subsSKUS) {
        if (inAppSKUS != null) {
            GoogleBillingUtil.inAppSKUS = Arrays.copyOf(inAppSKUS, inAppSKUS.length);
        }
        if (subsSKUS != null) {
            GoogleBillingUtil.subsSKUS = Arrays.copyOf(subsSKUS, subsSKUS.length);
        }
    }

    private static <T> void copyToArray(T[] base, T[] target) {
        System.arraycopy(base, 0, target, 0, base.length);
    }

    public static GoogleBillingUtil getInstance() {
        return mGoogleBillingUtil;
    }

    /**
     * 开始建立内购连接
     *
     * @param context
     * @param tag
     */
    public GoogleBillingUtil build(Context context, Object tag) {
        purchasesUpdatedListener.tag = getTag(tag);
        if (mBillingClient == null) {
            synchronized (mGoogleBillingUtil) {
                if (mBillingClient == null) {
                    builder = BillingClient.newBuilder(context.getApplicationContext());
                    mBillingClient = builder.setListener(purchasesUpdatedListener)
                            .enablePendingPurchases()
                            .build();
                } else {
                    builder.setListener(purchasesUpdatedListener);
                }
            }
        } else {
            builder.setListener(purchasesUpdatedListener);
        }
        synchronized (mGoogleBillingUtil) {
            if (mGoogleBillingUtil.startConnection(tag)) {
                mGoogleBillingUtil.queryInventoryInApp(getTag(tag));
                mGoogleBillingUtil.queryInventorySubs(getTag(tag));
                mGoogleBillingUtil.queryPurchasesInApp(getTag(tag));
            }
        }
        return mGoogleBillingUtil;
    }

    public boolean startConnection(Object tag) {
        return startConnection(getTag(tag));
    }

    private boolean startConnection(String tag) {
        if (mBillingClient == null) {
            log("初始化失败:mBillingClient==null");
            return false;
        }
        if (!mBillingClient.isReady()) {
            mBillingClient.startConnection(new BillingClientStateListener() {
                @Override
                public void onBillingSetupFinished(BillingResult billingResult) {
                    mainHandler.post(() -> {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                            synchronized (onGoogleBillingListenerList) {
                                for (OnGoogleBillingListener listener : onGoogleBillingListenerList) {
                                    listener.onSetupSuccess(listener.tag.equals(tag));
                                }
                            }

                            queryInventoryInApp(tag);
                            queryInventorySubs(tag);
                        } else {
                            log("初始化失败:onSetupFail:code=" + billingResult.getResponseCode());
                            mainHandler.post(() -> {
                                synchronized (onGoogleBillingListenerList) {
                                    for (OnGoogleBillingListener listener : onGoogleBillingListenerList) {
                                        listener.onFail(GoogleBillingListenerTag.SETUP, billingResult.getResponseCode(), listener.tag.equals(tag));
                                    }
                                }
                            });
                        }
                    });
                }

                @Override
                public void onBillingServiceDisconnected() {
                    mainHandler.post(() -> {
                        synchronized (onGoogleBillingListenerList) {
                            for (OnGoogleBillingListener listener : onGoogleBillingListenerList) {
                                listener.onBillingServiceDisconnected();
                            }
                        }
                        log("初始化失败:onBillingServiceDisconnected");
                    });
                }
            });
            return false;
        } else {
            return true;
        }
    }

    //endregion

    //region===================================查询商品=================================

    /**
     * 查询内购商品信息
     */
    public void queryInventoryInApp(Object tagObj) {
        queryInventoryInApp(getTag(tagObj));
    }

    private void queryInventoryInApp(String tag) {
        queryInventory(tag, BillingClient.ProductType.INAPP);
    }

    /**
     * 查询订阅商品信息
     */
    public void queryInventorySubs(Object tagObj) {
        queryInventory(getTag(tagObj), BillingClient.ProductType.SUBS);
    }

    private void queryInventorySubs(String tag) {
        queryInventory(tag, BillingClient.ProductType.SUBS);
    }


    /**
     * 查询购买的商品信息
     * @param tag
     * @param productType
     */
    private void queryInventory(String tag, final String productType) {
        boolean supportProduct = mBillingClient.isFeatureSupported(BillingClient.FeatureType.PRODUCT_DETAILS).getResponseCode()
                == BillingClient.BillingResponseCode.OK;
        if (supportProduct) {
            queryInventoryProduct(tag, BillingClient.ProductType.SUBS);
        } else {
            queryInventorySku(tag, BillingClient.ProductType.SUBS);
        }
    }

    private void queryInventorySku(String tag, final String productType) {
        Runnable runnable = () -> {
            mainHandler.post(() -> {
                if (mBillingClient == null) {
                    synchronized (onGoogleBillingListenerList) {
                        for (OnGoogleBillingListener listener : onGoogleBillingListenerList) {
                            listener.onError(GoogleBillingListenerTag.QUERY, listener.tag.equals(tag));
                        }
                    }
                    return;
                }
                ArrayList<String> skus = new ArrayList<>();
                if (productType.equals(BillingClient.ProductType.INAPP)) {
                    for (String productId : inAppSKUS) {
                        skus.add(productId);
                    }
                } else if (productType.equals(BillingClient.ProductType.SUBS)) {
                    for (String productId : inAppSKUS) {
                        skus.add(productId);
                    }
                }
                if (!skus.isEmpty()) {
                    SkuDetailsParams params = SkuDetailsParams.newBuilder()
                            .setSkusList(skus)
                            .setType(productType)
                            .build();
                    mBillingClient.querySkuDetailsAsync(params, new MySkuDetailsResponseListener(productType, tag));
                } else {
                    log("queryInventory=========productType=" + productType + "=====");
                }
            });
        };
        executeServiceRequest(tag, runnable);
    }

    private void queryInventoryProduct(String tag, final String productType) {
        Runnable runnable = () -> {
            mainHandler.post(() -> {
                if (mBillingClient == null) {
                    synchronized (onGoogleBillingListenerList) {
                        for (OnGoogleBillingListener listener : onGoogleBillingListenerList) {
                            listener.onError(GoogleBillingListenerTag.QUERY, listener.tag.equals(tag));
                        }
                    }
                    return;
                }
                ArrayList<QueryProductDetailsParams.Product> products = new ArrayList<>();
                QueryProductDetailsParams.Product product = null;
                if (productType.equals(BillingClient.ProductType.INAPP)) {
                    for (String productId : inAppSKUS) {
                        product = QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(productId)
                                .setProductType(BillingClient.ProductType.INAPP)
                                .build();
                        products.add(product);
                    }
                } else if (productType.equals(BillingClient.ProductType.SUBS)) {
                    for (String productId : inAppSKUS) {
                        product = QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(productId)
                                .setProductType(BillingClient.ProductType.INAPP)
                                .build();
                        products.add(product);
                    }
                }
                if (!products.isEmpty()) {
                    QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                            .setProductList(products)
                            .build();
                    mBillingClient.queryProductDetailsAsync(params, new MyProductDetailsResponseListener(productType, tag));
                } else {
                    log("queryInventory=========productType=" + productType + "=====");
                }
            });
        };
        executeServiceRequest(tag, runnable);
    }

    //endregion

    //region===================================购买商品=================================

    /**
     * 发起内购
     *
     * @param skuId 内购商品id
     */
    public void purchaseInApp(Activity activity, Object tagObj, String skuId) {
        purchase(activity, skuId, BillingClient.ProductType.INAPP, tagObj);
    }

    /**
     * 发起订阅
     *
     * @param skuId 订阅商品id
     */
    public void purchaseSubs(Activity activity, Object tagObj, String skuId) {
        purchase(activity, skuId, BillingClient.ProductType.SUBS, tagObj);
    }


    private void purchase(Activity activity, final String productId, final String productType, Object tagObj) {
        boolean supportProduct = mBillingClient.isFeatureSupported(BillingClient.FeatureType.PRODUCT_DETAILS).getResponseCode()
                == BillingClient.BillingResponseCode.OK;
        if (supportProduct) {
            purchaseProduct(activity, productId, productType, getTag(tagObj));
        } else {
            purchaseSku(activity, productId, productType, getTag(tagObj));
        }

    }

    private void purchaseSku(Activity activity, final String productId, final String productType, Object tagObj) {
        String tag = getTag(tagObj);
        if (mBillingClient == null) {
            mainHandler.post(() -> {
                synchronized (onGoogleBillingListenerList) {
                    for (OnGoogleBillingListener listener : onGoogleBillingListenerList) {
                        listener.onError(GoogleBillingListenerTag.PURCHASE, listener.tag.equals(tag));
                    }
                }
            });
            return;
        }
        if (startConnection(tag)) {
            purchasesUpdatedListener.tag = tag;
            builder.setListener(purchasesUpdatedListener);
            List<String> products = new ArrayList<>();
            products.add(productId);
            SkuDetailsParams params = SkuDetailsParams.newBuilder()
                    .setSkusList(products)
                    .setType(productType)
                    .build();
            mBillingClient.querySkuDetailsAsync(params, new SkuDetailsResponseListener() {
                @Override
                public void onSkuDetailsResponse(@NonNull BillingResult billingResult, @Nullable List<SkuDetails> productList) {
                    mainHandler.post(() -> {
                        if (productList != null && !productList.isEmpty()) {
                            BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                                    .setSkuDetails(productList.get(0))
                                    .build();
                            mBillingClient.launchBillingFlow(activity, flowParams);
                        } else {
                            log("purchase=========productType=" + productType + "=====productId=" + productId
                                    + "不可用=====tag=" + tag);
                        }
                    });
                }
            });
        } else {
            mainHandler.post(() -> {
                synchronized (onGoogleBillingListenerList) {
                    for (OnGoogleBillingListener listener : onGoogleBillingListenerList) {
                        listener.onError(GoogleBillingListenerTag.PURCHASE, listener.tag.equals(tag));
                    }
                }
            });
        }
    }


    private void purchaseProduct(Activity activity, final String productId, final String productType, Object tagObj) {
        String tag = getTag(tagObj);
        if (mBillingClient == null) {
            mainHandler.post(() -> {
                synchronized (onGoogleBillingListenerList) {
                    for (OnGoogleBillingListener listener : onGoogleBillingListenerList) {
                        listener.onError(GoogleBillingListenerTag.PURCHASE, listener.tag.equals(tag));
                    }
                }
            });
            return;
        }
        if (startConnection(tag)) {
            purchasesUpdatedListener.tag = tag;
            builder.setListener(purchasesUpdatedListener);
            List<QueryProductDetailsParams.Product> products = new ArrayList<>();
            QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                    .setProductType(productType).setProductId(productId).build();
            products.add(product);
            QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                    .setProductList(products)
                    .build();
            mBillingClient.queryProductDetailsAsync(params, new ProductDetailsResponseListener() {
                @Override
                public void onProductDetailsResponse(BillingResult billingResult, List<ProductDetails> productList) {
                    mainHandler.post(() -> {
                        if (productList != null && !productList.isEmpty()) {
                            List<BillingFlowParams.ProductDetailsParams> paramsList = new ArrayList();

                            ProductDetails productDetails = productList.get(0);
                            String offerToken = "";
                            if (productDetails.getSubscriptionOfferDetails() != null) {
                                for (int i = 0; i < productDetails.getSubscriptionOfferDetails().size(); i++) {
                                    offerToken = productDetails.getSubscriptionOfferDetails().get(i).getOfferToken();
                                    if (!offerToken.isEmpty()) {
                                        break;
                                    }
                                }
                            }
                            BillingFlowParams.ProductDetailsParams param = BillingFlowParams.ProductDetailsParams.newBuilder()
                                    .setProductDetails(productList.get(0))
                                    .setOfferToken(offerToken)
                                    .build();
                            paramsList.add(param);
                            BillingFlowParams flowParams = BillingFlowParams.newBuilder()
//                                .setSkuDetails(productList.get(0))
                                    .setProductDetailsParamsList(paramsList)
                                    .build();
                            mBillingClient.launchBillingFlow(activity, flowParams);
                        } else {
                            log("purchase=========productType=" + productType + "=====productId=" + productId
                                    + "===不可用=====tag=" + tag);
                        }
                    });
                }
            });
        } else {
            mainHandler.post(() -> {
                synchronized (onGoogleBillingListenerList) {
                    for (OnGoogleBillingListener listener : onGoogleBillingListenerList) {
                        listener.onError(GoogleBillingListenerTag.PURCHASE, listener.tag.equals(tag));
                    }
                }
            });
        }
    }


    //endregion

    //region===================================消耗商品=================================

    /**
     * 消耗商品
     *
     * @param purchaseToken {@link Purchase#getPurchaseToken()}
     */
    public void consumeAsync(Object tag, String purchaseToken) {
        consumeAsync(getTag(tag), purchaseToken, null);
    }


    public void consumeAsync(Object tag, String purchaseToken, @Nullable String developerPayload) {
        consumeAsync(getTag(tag), purchaseToken, developerPayload);
    }

    /**
     * 消耗商品
     *
     * @param purchaseToken {@link Purchase#getPurchaseToken()}
     */
    private void consumeAsync(String tag, String purchaseToken) {
        consumeAsync(tag, purchaseToken, null);
    }


    private void consumeAsync(String tag, String purchaseToken, @Nullable String developerPayload) {
        if (mBillingClient == null) {
            return;
        }
        ConsumeParams consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchaseToken)
                .build();
        mBillingClient.consumeAsync(consumeParams, new MyConsumeResponseListener(tag));
    }

    /**
     * 消耗内购商品-通过sku数组
     *
     * @param sku sku
     */
    public void consumeAsyncInApp(Object tag, @NonNull String... sku) {
        if (mBillingClient == null) {
            return;
        }
        List<String> skuList = Arrays.asList(sku);
        consumeAsyncInApp(tag, skuList, null);
    }

    /**
     * 消耗内购商品-通过sku数组
     *
     * @param productList sku数组
     */
    public void consumeAsyncInApp(Object tag, @NonNull List<String> productList, @Nullable List<String> developerPayloadList) {
        if (mBillingClient == null) {
            return;
        }
        queryPurchasesInApp(getTag(tag), new PurchasesResponseListener() {
            @Override
            public void onQueryPurchasesResponse(BillingResult billingResult, List<Purchase> purchaseList) {
                mainHandler.post(() -> {
                    if (purchaseList != null) {
                        for (Purchase purchase : purchaseList) {
                            int index = productList.indexOf(purchase.getProducts().get(0));
                            if (index != -1) {
                                if (developerPayloadList != null && index < developerPayloadList.size()) {
                                    consumeAsync(getTag(tag), purchase.getPurchaseToken(), developerPayloadList.get(index));
                                } else {
                                    consumeAsync(getTag(tag), purchase.getPurchaseToken(), null);
                                }
                            }
                        }
                    }
                });
            }
        });

    }

    //endregion

    //region===================================确认购买=================================

    /**
     * 确认购买
     *
     * @param tag
     * @param purchaseToken
     */
    public void acknowledgePurchase(Object tag, String purchaseToken) {
        acknowledgePurchase(tag, purchaseToken, null);
    }

    public void acknowledgePurchase(Object tag, String purchaseToken, @Nullable String developerPayload) {
        acknowledgePurchase(getTag(tag), purchaseToken, developerPayload);
    }

    private void acknowledgePurchase(String tag, String purchaseToken) {
        acknowledgePurchase(tag, purchaseToken, null);
    }

    private void acknowledgePurchase(String tag, String purchaseToken, @Nullable String developerPayload) {
        if (mBillingClient == null) {
            return;
        }
        AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchaseToken)
                .build();
        mBillingClient.acknowledgePurchase(params, new MyAcknowledgePurchaseResponseListener(tag));
    }

    //endregion

    //region===================================本地订单查询=================================

    /**
     * 获取已经内购的商品
     *
     * @return 商品列表
     */
//    public List<Purchase> queryPurchasesInApp(Object tag) {
    public void queryPurchasesInApp(Object tag, PurchasesResponseListener purchasesResponseListener) {
        queryPurchases(getTag(tag), BillingClient.ProductType.INAPP, purchasesResponseListener);
    }

    private void queryPurchasesInApp(String tag) {
        queryPurchases(tag, BillingClient.ProductType.INAPP, null);
    }

    /**
     * 获取已经订阅的商品
     *
     * @return 商品列表
     */
    public void queryPurchasesSubs(Object tag) {
        queryPurchases(getTag(tag), BillingClient.ProductType.SUBS, null);
    }

    private void queryPurchasesSubs(String tag) {
        queryPurchases(tag, BillingClient.ProductType.SUBS, null);
    }

    private void queryPurchases(String tag, String productType, PurchasesResponseListener purchasesResponseListener) {
        if (mBillingClient == null) {
            return;
        }
        if (!mBillingClient.isReady()) {
            startConnection(tag);
        } else {
            mBillingClient.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(productType).build(), new PurchasesResponseListener() {
                @Override
                public void onQueryPurchasesResponse(BillingResult billingResult, List<Purchase> purchaseList) {
                    mainHandler.post(() -> {
                        if (purchasesResponseListener != null) {
                            purchasesResponseListener.onQueryPurchasesResponse(billingResult, purchaseList);
                            return;
                        }
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                            if (purchaseList != null && !purchaseList.isEmpty()) {
                                int position = 0;
                                for (Purchase purchase : purchaseList) {
                                    synchronized (onGoogleBillingListenerList) {
                                        for (OnGoogleBillingListener listener : onGoogleBillingListenerList) {
                                            boolean isSelf = listener.tag.equals(tag);//是否是当前页面
                                            boolean isSuccess = listener.onRecheck(productType, purchase, position == purchaseList.size() - 1, isSelf);//是否消耗或者确认
                                            if (isSelf) {
                                                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                                                    if (productType.equals(BillingClient.ProductType.INAPP)) {
                                                        if (isSuccess) {
                                                            consumeAsync(tag, purchase.getPurchaseToken());
                                                        } else if (isAutoAcknowledgePurchase) {
                                                            if (!purchase.isAcknowledged()) {
                                                                acknowledgePurchase(tag, purchase.getPurchaseToken());
                                                            }
                                                        }
                                                    } else if (productType.equals(BillingClient.ProductType.SUBS)) {
                                                        if (isAutoAcknowledgePurchase) {
                                                            if (!purchase.isAcknowledged()) {
                                                                acknowledgePurchase(tag, purchase.getPurchaseToken());
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    log("未支付的订单:" + purchase.getProducts().get(0));
                                                }
                                            }
                                        }
                                        position++;
                                    }
                                }
                            }
                        }
                    });
                }
            });
        }
    }
    //endregion

    //region===================================在线订单查询=================================

    /**
     * 异步联网查询所有的内购历史-无论是过期的、取消、等等的订单
     *
     * @param tag
     */
    public boolean queryPurchaseHistoryAsyncInApp(Object tag) {
        return queryPurchaseHistoryAsync(getTag(tag), BILLING_TYPE_INAPP);
    }

    /**
     * 异步联网查询所有的订阅历史-无论是过期的、取消、等等的订单
     *
     * @param tag
     */
    public boolean queryPurchaseHistoryAsyncSubs(Object tag) {
        return queryPurchaseHistoryAsync(getTag(tag), BILLING_TYPE_SUBS);
    }

    private boolean queryPurchaseHistoryAsync(String tag, String productType) {
        if (isReady()) {
            QueryPurchaseHistoryParams param = QueryPurchaseHistoryParams.newBuilder()
                    .setProductType(productType).build();
            mBillingClient.queryPurchaseHistoryAsync(param, new MyPurchaseHistoryResponseListener(tag));
            return true;
        }
        return false;
    }

    //endregion

    //region===================================工具集合=================================

    /**
     * 获取有效订阅的数量
     *
     * @return -1查询失败，0没有有效订阅，>0具有有效的订阅
     */
    public void getPurchasesSizeSubs(Object tagObj) {
        queryPurchases(getTag(tagObj), BillingClient.ProductType.SUBS, new PurchasesResponseListener() {
            @Override
            public void onQueryPurchasesResponse(BillingResult billingResult, List<Purchase> list) {
                mainHandler.post(() -> {
                    synchronized (onGoogleBillingListenerList) {
                        int count = -1;
                        if (list != null) {
                            count = list.size();
                        }
                        for (OnGoogleBillingListener listener : onGoogleBillingListenerList) {
                            listener.onSubsCountListener(count);
                        }
                    }
                });
            }
        });
    }


    /**
     * 通过product获取订阅商品序号
     *
     * @param product sku
     * @return 序号
     */
    public int getSubsPositionBySku(String product) {
        return getPositionBySku(product, BillingClient.ProductType.SUBS);
    }

    /**
     * 通过product获取内购商品序号
     *
     * @param product sku
     * @return 成功返回需要 失败返回-1
     */
    public int getInAppPositionBySku(String product) {
        return getPositionBySku(product, BillingClient.ProductType.INAPP);
    }

    private int getPositionBySku(String product, String productType) {

        if (productType.equals(BillingClient.ProductType.INAPP)) {
            int i = 0;
            for (String s : inAppSKUS) {
                if (s.equals(product)) {
                    return i;
                }
                i++;
            }
        } else if (productType.equals(BillingClient.ProductType.SUBS)) {
            int i = 0;
            for (String s : subsSKUS) {
                if (s.equals(product)) {
                    return i;
                }
                i++;
            }
        }
        return -1;
    }

    /**
     * 通过序号获取订阅sku
     *
     * @param position 序号
     * @return sku
     */
    public String getSubsSkuByPosition(int position) {
        if (position >= 0 && position < subsSKUS.length) {
            return subsSKUS[position];
        } else {
            return null;
        }
    }

    /**
     * 通过序号获取内购sku
     *
     * @param position 序号
     * @return sku
     */
    public String getInAppSkuByPosition(int position) {
        if (position >= 0 && position < inAppSKUS.length) {
            return inAppSKUS[position];
        } else {
            return null;
        }
    }

    /**
     * 通过sku获取商品类型(订阅获取内购)
     *
     * @param sku sku
     * @return inapp内购，subs订阅
     */
    public String getSkuType(String sku) {
        if (Arrays.asList(inAppSKUS).contains(sku)) {
            return BillingClient.ProductType.INAPP;
        } else if (Arrays.asList(subsSKUS).contains(sku)) {
            return BillingClient.ProductType.SUBS;
        }
        return null;
    }

    private String getTag(Object tag) {
        return tag.toString();
    }

    //endregion

    //region===================================其他方法=================================

    private void executeServiceRequest(String tag, final Runnable runnable) {
        if (startConnection(tag)) {
            runnable.run();
        }
    }


    /**
     * google内购服务是否已经准备好
     *
     * @return boolean
     */
    public static boolean isReady() {
        return mBillingClient != null && mBillingClient.isReady();
    }

    /**
     * 设置是否自动确认购买
     *
     * @param isAutoAcknowledgePurchase boolean
     */
    public static void setIsAutoAcknowledgePurchase(boolean isAutoAcknowledgePurchase) {
        GoogleBillingUtil.isAutoAcknowledgePurchase = isAutoAcknowledgePurchase;
    }

    /**
     * 断开连接google服务
     * 注意！！！一般情况不建议调用该方法，让google保留连接是最好的选择。
     */
    public static void endConnection() {
        //注意！！！一般情况不建议调用该方法，让google保留连接是最好的选择。
        if (mBillingClient != null) {
            if (mBillingClient.isReady()) {
                mBillingClient.endConnection();
                mBillingClient = null;
            }
        }
    }

    //endregion

    public GoogleBillingUtil addOnGoogleBillingListener(Activity activity, OnGoogleBillingListener onGoogleBillingListener) {
        onGoogleBillingListener.tag = getTag(activity);
        synchronized (onGoogleBillingListenerList) {
            onGoogleBillingListenerList.add(onGoogleBillingListener);
        }
        return this;
    }

    public void removeOnGoogleBillingListener(OnGoogleBillingListener onGoogleBillingListener) {
        synchronized (onGoogleBillingListenerList) {
            onGoogleBillingListenerList.remove(onGoogleBillingListener);
        }
    }

    public void removeOnGoogleBillingListener(Object tagObj) {
        String tag = getTag(tagObj);
        for (int i = onGoogleBillingListenerList.size() - 1; i >= 0; i--) {
            OnGoogleBillingListener listener = onGoogleBillingListenerList.get(i);
            if (listener.tag.equals(tag)) {
                removeOnGoogleBillingListener(listener);
            }
        }
    }


    /**
     * 清除内购监听器，防止内存泄漏-在Activity-onDestroy里面调用。
     * 需要确保onDestroy和build方法在同一个线程。
     */
    public void onDestroy(Activity activity) {
        if (builder != null) {
            builder.setListener(null);
        }
        removeOnGoogleBillingListener(activity);
    }

    /**
     * Google购买商品回调接口(订阅和内购都走这个接口)
     */
    private class MyPurchasesUpdatedListener implements PurchasesUpdatedListener {

        public String tag;

        @Override
        public void onPurchasesUpdated(BillingResult billingResult, @Nullable List<Purchase> list) {
            mainHandler.post(() -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && list != null) {
                    for (Purchase purchase : list) {
                        synchronized (onGoogleBillingListenerList) {
                            for (OnGoogleBillingListener listener : onGoogleBillingListenerList) {
                                boolean isSelf = listener.tag.equals(tag);//是否是当前页面
                                boolean isSuccess = listener.onPurchaseSuccess(purchase, isSelf);//是否自动消耗
                                if (isSelf && purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                                    //是当前页面，并且商品状态为支付成功，才会进行消耗与确认的操作
                                    String productType = getSkuType(purchase.getProducts().get(0));
                                    if (BillingClient.ProductType.INAPP.equals(productType)) {
                                        if (isSuccess) {
                                            //进行消耗
                                            consumeAsync(tag, purchase.getPurchaseToken());
                                        } else if (isAutoAcknowledgePurchase) {
                                            //进行确认购买
                                            if (!purchase.isAcknowledged()) {
                                                acknowledgePurchase(tag, purchase.getPurchaseToken());
                                            }
                                        }
                                    } else if (BillingClient.ProductType.SUBS.equals(productType)) {
                                        //进行确认购买
                                        if (isAutoAcknowledgePurchase) {
                                            if (!purchase.isAcknowledged()) {
                                                acknowledgePurchase(tag, purchase.getPurchaseToken());
                                            }
                                        }
                                    }
                                } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                                    log("待处理的订单:" + purchase.getProducts().get(0));
                                }
                            }
                        }
                    }
                } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                    if (list != null) {
                        queryPurchases(purchasesUpdatedListener.tag, getSkuType(list.get(0).getProducts().get(0)), null);
                    }
                } else {
                    if (IS_DEBUG) {
                        log("购买失败,responseCode:" + billingResult.getResponseCode() + ",msg:" + billingResult.getDebugMessage());
                    }
                    synchronized (onGoogleBillingListenerList) {
                        for (OnGoogleBillingListener listener : onGoogleBillingListenerList) {
                            listener.onFail(GoogleBillingListenerTag.PURCHASE, billingResult.getResponseCode(), listener.tag.equals(tag));
                        }
                    }
                }
            });
        }

    }

    /**
     * Google查询商品信息回调接口(新)
     */
    private class MyProductDetailsResponseListener implements ProductDetailsResponseListener {

        private String skuType;
        private String tag;

        public MyProductDetailsResponseListener(String skuType, String tag) {
            this.skuType = skuType;
            this.tag = tag;
        }

        @Override
        public void onProductDetailsResponse(BillingResult billingResult, List<ProductDetails> list) {
            mainHandler.post(() -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && list != null) {
                    synchronized (onGoogleBillingListenerList) {
                        for (OnGoogleBillingListener listener : onGoogleBillingListenerList) {
                            listener.onQueryProductSuccess(skuType, list, listener.tag.equals(tag));
                        }
                    }
                } else {
                    synchronized (onGoogleBillingListenerList) {
                        for (OnGoogleBillingListener listener : onGoogleBillingListenerList) {
                            listener.onFail(GoogleBillingListenerTag.QUERY, billingResult.getResponseCode(), listener.tag.equals(tag));
                        }
                        if (IS_DEBUG) {
                            log("查询失败,responseCode:" + billingResult.getResponseCode() + ",msg:" + billingResult.getDebugMessage());
                        }
                    }
                }
            });
        }
    }

    /**
     * Google查询商品信息回调接口
     */
    @Deprecated
    private class MySkuDetailsResponseListener implements SkuDetailsResponseListener {
        private String skuType;
        private String tag;

        public MySkuDetailsResponseListener(String skuType, String tag) {
            this.skuType = skuType;
            this.tag = tag;
        }

        @Override
        public void onSkuDetailsResponse(BillingResult billingResult, List<SkuDetails> list) {
            mainHandler.post(() -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && list != null) {
                    synchronized (onGoogleBillingListenerList) {
                        for (OnGoogleBillingListener listener : onGoogleBillingListenerList) {
                            listener.onQuerySkuSuccess(skuType, list, listener.tag.equals(tag));
                        }
                    }
                } else {
                    synchronized (onGoogleBillingListenerList) {
                        for (OnGoogleBillingListener listener : onGoogleBillingListenerList) {
                            listener.onFail(GoogleBillingListenerTag.QUERY, billingResult.getResponseCode(), listener.tag.equals(tag));
                        }
                        if (IS_DEBUG) {
                            log("查询失败,responseCode:" + billingResult.getResponseCode() + ",msg:" + billingResult.getDebugMessage());
                        }
                    }
                }
            });
        }

    }

    /**
     * Googlg消耗商品回调
     */
    private class MyConsumeResponseListener implements ConsumeResponseListener {
        private String tag;

        public MyConsumeResponseListener(String tag) {
            this.tag = tag;
        }

        @Override
        public void onConsumeResponse(BillingResult billingResult, String purchaseToken) {
            mainHandler.post(() -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    synchronized (onGoogleBillingListenerList) {
                        for (OnGoogleBillingListener listener : onGoogleBillingListenerList) {
                            listener.onConsumeSuccess(purchaseToken, listener.tag.equals(tag));
                        }
                    }
                } else {
                    synchronized (onGoogleBillingListenerList) {
                        for (OnGoogleBillingListener listener : onGoogleBillingListenerList) {
                            listener.onFail(GoogleBillingListenerTag.CONSUME, billingResult.getResponseCode(), listener.tag.equals(tag));
                        }
                    }
                    if (IS_DEBUG) {
                        log("消耗失败,responseCode:" + billingResult.getResponseCode() + ",msg:" + billingResult.getDebugMessage());
                    }
                }
            });
        }
    }

    /**
     * Google消耗商品回调
     */
    private class MyAcknowledgePurchaseResponseListener implements AcknowledgePurchaseResponseListener {

        private String tag;

        public MyAcknowledgePurchaseResponseListener(String tag) {
            this.tag = tag;
        }

        @Override
        public void onAcknowledgePurchaseResponse(BillingResult billingResult) {
            mainHandler.post(() -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    synchronized (onGoogleBillingListenerList) {
                        for (OnGoogleBillingListener listener : onGoogleBillingListenerList) {
                            listener.onAcknowledgePurchaseSuccess(listener.tag.equals(tag));
                        }
                    }
                } else {
                    synchronized (onGoogleBillingListenerList) {
                        for (OnGoogleBillingListener listener : onGoogleBillingListenerList) {
                            listener.onFail(GoogleBillingListenerTag.AcKnowledgePurchase, billingResult.getResponseCode(), listener.tag.equals(tag));
                        }
                    }
                    if (IS_DEBUG) {
                        log("确认购买失败,responseCode:" + billingResult.getResponseCode() + ",msg:" + billingResult.getDebugMessage());
                    }
                }
            });
        }
    }

    private class MyPurchaseHistoryResponseListener implements PurchaseHistoryResponseListener {

        private String tag;

        public MyPurchaseHistoryResponseListener(String tag) {
            this.tag = tag;
        }

        @Override
        public void onPurchaseHistoryResponse(BillingResult billingResult, List<PurchaseHistoryRecord> list) {
            mainHandler.post(() -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && list != null) {
                    synchronized (onGoogleBillingListenerList) {
                        for (OnGoogleBillingListener listener : onGoogleBillingListenerList) {
                            listener.onQueryHistory(list);
                        }
                    }
                } else {
                    synchronized (onGoogleBillingListenerList) {
                        for (OnGoogleBillingListener listener : onGoogleBillingListenerList) {
                            listener.onFail(GoogleBillingListenerTag.HISTORY, billingResult.getResponseCode(), listener.tag.equals(tag));
                        }
                    }
                }
            });
        }
    }

    public enum GoogleBillingListenerTag {

        QUERY("query"),
        PURCHASE("purchase"),
        SETUP("setup"),
        CONSUME("consume"),
        AcKnowledgePurchase("AcKnowledgePurchase"),
        HISTORY("history"),
        ;
        public String tag;

        GoogleBillingListenerTag(String tag) {
            this.tag = tag;
        }

    }

    private static void log(String msg) {
        if (IS_DEBUG) {
            Log.e(TAG, msg);
        }
    }

    public static void isDebug(boolean isDebug) {
        GoogleBillingUtil.IS_DEBUG = isDebug;
    }
}
