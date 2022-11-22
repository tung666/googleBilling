# GoogleBilling

##### 根据[GoogleBilling](https://gitee.com/tjbaobao/GoogleBilling/tree/master/) 进行部分修改,Google billing库升级到5.x.x版本



```

    //不支持 BillingClient.FeatureType.PRODUCT_DETAILS 的查询商品回调方法
    public void onQuerySkuSuccess(@NonNull String productType, @NonNull List<SkuDetails> list, boolean isSelf){}
    
    //支持 BillingClient.FeatureType.PRODUCT_DETAILS 的查询商品回调方法
    public void onQueryProductSuccess(@NonNull String productType, @NonNull List<ProductDetails> list, boolean isSelf){}

```