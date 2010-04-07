/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
 
 import java.lang.*;
 import java.util.*;
 import org.ofbiz.base.util.*;
 import org.ofbiz.entity.*;
 import org.ofbiz.service.*;
 import org.ofbiz.product.catalog.*;
 import org.ofbiz.product.category.*;
 import org.ofbiz.product.product.ProductWorker;
 import org.ofbiz.product.product.ProductContentWrapper;
 import org.ofbiz.product.catalog.*;
 import org.ofbiz.ebaystore.EbayStoreHelper;
 import com.ebay.sdk.ApiContext;
 import com.ebay.sdk.call.AddItemCall;
 import com.ebay.soap.eBLBaseComponents.ItemType;
 import com.ebay.soap.eBLBaseComponents.CategoryType;
 import com.ebay.soap.eBLBaseComponents.ListingTypeCodeType;
 import com.ebay.soap.eBLBaseComponents.BuyerPaymentMethodCodeType;
 import com.ebay.soap.eBLBaseComponents.ItemSpecificsEnabledCodeType;
 import com.ebay.soap.eBLBaseComponents.GeteBayDetailsResponseType;
 import com.ebay.soap.eBLBaseComponents.ReturnPolicyType;
 import com.ebay.soap.eBLBaseComponents.SiteCodeType;
 import com.ebay.soap.eBLBaseComponents.ShippingServiceDetailsType;
 import com.ebay.soap.eBLBaseComponents.ShippingLocationDetailsType;
 import org.ofbiz.ebaystore.EbayEvents;
 import com.ebay.sdk.ApiException;
 import com.ebay.sdk.SdkException;

//set the content path prefix
 contentPathPrefix = CatalogWorker.getContentPathPrefix(request);
 productStoreId = parameters.productStoreId;
 search_CategoryId = parameters.search_CategoryId;
 
//Get the addItemList and Prepare details
 apiContext = EbayEvents.getApiContext(request);
 addItemObj = EbayEvents.getAddItemListingObject(request, apiContext);
 contents = [];
 if (addItemObj) {
     site = (SiteCodeType)apiContext.getSite();
     context.site = site;
     context.siteCode = apiContext.getSite().value();
     context.siteCode_Ebay_Motors = SiteCodeType.E_BAY_MOTORS.value();
     addItems = addItemObj.itemListing;
     addItems.each{ addItemMap ->
         addItem = addItemMap.addItemCall;
         content = [:];
         item = addItem.getItem();
         productId = item.getSKU();
         product = delegator.findByPrimaryKeyCache("Product", [productId : productId]);
         contentWrapper = new ProductContentWrapper(product, request);
         content.productContentWrapper = contentWrapper;
         content.product = product;
         contents.add(content);
     }

     request.setAttribute("productStoreId", productStoreId);
     categories = EbayEvents.getChildCategories(request);
     context.categories = categories;
     
     
     // point product tab id 
     productId = null;
     if (request.getAttribute("isProductId") || parameters.isProductId) {
         if (request.getAttribute("isProductId")) {
             productId = request.getAttribute("isProductId");
         } else {
             productId = parameters.isProductId;
         }
         context.isProductId = productId;
         // get product default price form product price 
         productPrices = delegator.findByAnd("ProductPrice",["productId":productId,"productPricePurposeId":"EBAY"]);
         if (productPrices) {
             context.productPrices = productPrices;
         }
         //Is it reserve on eBayInventory
         isReserve = EbayStoreHelper.isReserveInventory(delegator, productStoreId, productId);
         context.isReserve = isReserve;
         // get category detail 
         pkCateId = null;
         stCate1ID = null;
         stCate2ID = null;
         addItems.each{ addItemMap ->
             addItem = addItemMap.addItemCall;
             item = addItem.getItem();
             if (productId == item.getSKU()) {
                 primaryCate = item.getPrimaryCategory();
                 storeFront = item.getStorefront();
                 if (storeFront) {
                     stCate1ID = storeFront.getStoreCategoryID();
                     stCate2ID = storeFront.getStoreCategory2ID();
                     context.storeFront = storeFront;
                 }
                 if (primaryCate) pkCateId = primaryCate.getCategoryID();
             }
         }
         context.stCate1ID = stCate1ID;
         context.stCate2ID = stCate2ID;
         if (pkCateId) {
         refName = "itemCateFacade_" + pkCateId;
             if (addItemObj.get(refName)) {
                    cf = addItemObj.get(refName);
                    if (cf) {
                        listingTypes = [];
                        listingDurationReferenceMap = cf.getListingDurationReferenceMap();
                        listingDurationMap = cf.getListingDurationMap();
                        if (listingDurationReferenceMap && listingDurationMap) {
                            Iterator keys = listingDurationReferenceMap.keySet().iterator();
                            while (keys.hasNext()) {
                                listingTypeMap = [:];
                                String key = keys.next();
                                listingTypeMap.type = key;
                                listingTypeMap.durations = listingDurationMap.get(new Integer(listingDurationReferenceMap.get(key)));
                                listingTypes.add(listingTypeMap);
                            }
                        }
                        context.listingTypes = listingTypes;
                        buyerPaymentMethods = cf.getPaymentMethods();
                        if (buyerPaymentMethods) {
                            context.paymentMethods = buyerPaymentMethods;
                            context.buyerPayMethCode_PAY_PAL = BuyerPaymentMethodCodeType.PAY_PAL;
                        }
                        // load item specifices and custom if both item attributes and item specifics are supported, we only use item specifics
                        if (cf.getItemSpecificEnabled()) {
                            if (ItemSpecificsEnabledCodeType.ENABLED.equals(cf.getItemSpecificEnabled())) {
                            } else if (cf.AttributesEnabled()) { //show attributes input page
                                String attributesHtml = attrMaster.renderHtml(cf.getJoinedAttrSets(), null);
                                context.AttributesHtml = attributesHtml;
                            } else {//show return policy input page
                                sf = EbayEvents.getSiteFacade(apiContext,request);
                                details = sf.getEBayDetailsMap().get(apiContext.getSite());
                                returnPolicyEnabled = cf.getRetPolicyEnabled();
                                
                                context.EBayDetails = details;
                                context.ReturnPolicyEnabled = returnPolicyEnabled;
                            }
                        }
                       // load shipping services detail 
                        eBayDetailsMap = sf.getEBayDetailsMap();
                        if (eBayDetailsMap) {
                            eBayDetails = eBayDetailsMap.get(site);
                            shippingServiceDetails = eBayDetails.getShippingServiceDetails();
                            shippingServiceDetails = EbayEvents.filterShippingService(shippingServiceDetails);
                            context.shippingServiceDetails = shippingServiceDetails;
                            shippingLocationDetails = eBayDetails.getShippingLocationDetails();
                            context.shippingLocationDetails = shippingLocationDetails;
                        }
                       // load AdItemTemplates
                        if (cf.getAdItemTemplates()) {
                            context.adItemTemplates = cf.getAdItemTemplates();
                        }
                    }
             }
         }
     }
 }
 context.addItemObj = addItemObj;
 context.contentList = contents;
