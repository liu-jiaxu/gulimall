package com.atguigu.gulimall.product.service.impl;

import com.atguigu.gulimall.common.utils.PageUtils;
import com.atguigu.gulimall.common.utils.Query;
import com.atguigu.gulimall.common.utils.R;
import com.atguigu.gulimall.api.sku.SkuReductionTo;
import com.atguigu.gulimall.api.spu.SpuBoundTo;
import com.atguigu.gulimall.product.dao.AttrDao;
import com.atguigu.gulimall.product.dao.SpuInfoDao;
import com.atguigu.gulimall.product.entity.*;
import com.atguigu.gulimall.product.feign.CouponFeignClient;
import com.atguigu.gulimall.product.service.*;
import com.atguigu.gulimall.product.vo.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mysql.cj.util.StringUtils;
import lombok.Builder;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service("spuInfoService")
@Builder
public class SpuInfoServiceImpl extends ServiceImpl<SpuInfoDao, SpuInfoEntity> implements SpuInfoService {

    private SpuInfoDao spuInfoDao;
    /*
        serviceImpl中可以引用其它service，但仅可用来调用框架内置的DB方法（selectById等等）
        因为这些方法都是框架IService中的，不会循环依赖，当然也可以用dao层的DB方法（功能都一样）

        对于手动编写的方法，service间需要相互调用是不可以的（防止循环依赖），需要将可复用逻辑下沉method层
        method不同于utils，method包含业务逻辑，属于组件，utils只是常规共通方法，全部属于static，不是组件

        像一些大型项目还会有usecase层，用于调用service，编排流程和实现事务，controller层只负责与http交互
        对于vo实体类的业务检查，在service中新增check方法，usecase调用即可
     */
    private SpuInfoDescService spuInfoDescService;
    private SpuImagesService spuImagesService;
    private AttrService attrService;
    private ProductAttrValueService productAttrValueService;
    private SkuInfoService skuInfoService;
    private SkuImagesService skuImagesService;
    private SkuSaleAttrValueService skuSaleAttrValueService;
    private CouponFeignClient couponFeignClient;
    private AttrDao attrDao;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<SpuInfoEntity> page = this.page(
                new Query<SpuInfoEntity>().getPage(params),
                new QueryWrapper<>()
        );

        return new PageUtils(page);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveSpuSaveVo(SpuSaveVo spuSaveVo) {
        //1、保存spu基本信息`pms_spu_info`
        SpuInfoEntity infoEntity = new SpuInfoEntity();
        BeanUtils.copyProperties(spuSaveVo, infoEntity);
        infoEntity.setCreateTime(new Date());
        infoEntity.setUpdateTime(new Date());
        this.save(infoEntity);

        //2、保存spu的描述图片`pms_spu_info_desc`
        List<String> decript = spuSaveVo.getDecript();
        SpuInfoDescEntity descEntity = new SpuInfoDescEntity();
        descEntity.setSpuId(infoEntity.getId());
        descEntity.setDecript(String.join(",", decript));
        spuInfoDescService.save(descEntity);

        //3、保存spu的图片集`pms_spu_images`
        List<String> images = spuSaveVo.getImages();
        if (images != null && !images.isEmpty()) {
            List<SpuImagesEntity> collect = images.stream().map(image -> {
                SpuImagesEntity imagesEntity = new SpuImagesEntity();
                imagesEntity.setSpuId(infoEntity.getId());
                imagesEntity.setImgUrl(image);
                return imagesEntity;
            }).toList();
            spuImagesService.saveBatch(collect);
        }

        //4、保存spu的规格参数`pms_product_attr_value`
        List<BaseAttrs> baseAttrs = spuSaveVo.getBaseAttrs();
        // 解决循环DB调用
        Map<String, String> attrNameMap = baseAttrs.stream()
                .collect(Collectors.toMap(
                        baseAttr -> baseAttr.getAttrId().toString(),
                        baseAttr -> attrDao.getAttrNameById(baseAttr.getAttrId())
                ));

        List<ProductAttrValueEntity> productAttrValueEntityList = baseAttrs.stream().map(attr -> {
            ProductAttrValueEntity valueEntity = new ProductAttrValueEntity();
            valueEntity.setAttrId(attr.getAttrId());
//            AttrEntity byId = attrService.getById(attr.getAttrId());
//            valueEntity.setAttrName(byId.getAttrName());
            valueEntity.setAttrName(attrNameMap.get(attr.getAttrId().toString()));
            valueEntity.setAttrValue(attr.getAttrValues());
            valueEntity.setQuickShow(attr.getShowDesc());
            valueEntity.setSpuId(infoEntity.getId());
            return valueEntity;
        }).collect(Collectors.toList());
        productAttrValueService.saveBatch(productAttrValueEntityList);

        //5、保存spu的积分信息`gulimall_sms`->`sms_spu_bounds`
        Bounds bounds = spuSaveVo.getBounds();
        SpuBoundTo spuBoundTo = new SpuBoundTo();
        BeanUtils.copyProperties(bounds, spuBoundTo);
        spuBoundTo.setSpuId(infoEntity.getId());
        R r = couponFeignClient.saveSpuBounds(spuBoundTo);
        if (r.getCode() != 0){
            log.error("远程保存spu积分信息失败");
        }

        //6、保存spu对应的所有sku信息
        List<Skus> skus = spuSaveVo.getSkus();
        if (skus != null && !skus.isEmpty()) {
            skus.forEach(item -> {
                String defaultImg = "";
                //查找出默认图片
                for (Images image : item.getImages()) {
                    if (image.getDefaultImg() == 1) {
                        defaultImg = image.getImgUrl();
                    }
                }
                SkuInfoEntity skuInfoEntity = new SkuInfoEntity();
                BeanUtils.copyProperties(item, skuInfoEntity);
                skuInfoEntity.setSpuId(infoEntity.getId());
                skuInfoEntity.setBrandId(infoEntity.getBrandId());
                skuInfoEntity.setCatelogId(infoEntity.getCatelogId());
                skuInfoEntity.setSaleCount(0L);
                skuInfoEntity.setSkuDefaultImg(defaultImg);
                //6.1、sku的基本信息`pms_sku_info`
                skuInfoService.save(skuInfoEntity);

                //6.2、sku的图片信息`pms_sku_images`
                Long skuId = skuInfoEntity.getSkuId();

                List<SkuImagesEntity> skuImagesEntities = item.getImages().stream().map(img -> {
                    SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                    skuImagesEntity.setSkuId(skuId);
                    skuImagesEntity.setImgUrl(img.getImgUrl());
                    skuImagesEntity.setDefaultImg(img.getDefaultImg());
                    return skuImagesEntity;
                }).filter(entity -> {
                    //返回true是需要，返回false是过滤掉
                    return !StringUtils.isNullOrEmpty(entity.getImgUrl());
                }).collect(Collectors.toList());
                skuImagesService.saveBatch(skuImagesEntities);

                //6.3、sku的销售属性信息`pms_sku_sale_attr_value`
                List<Attr> attr = item.getAttr();
                List<SkuSaleAttrValueEntity> skuSaleAttrValueEntities = attr.stream().map(a -> {
                    SkuSaleAttrValueEntity skuSaleAttrValueEntity = new SkuSaleAttrValueEntity();
                    skuSaleAttrValueEntity.setSkuId(skuId);
                    BeanUtils.copyProperties(a, skuSaleAttrValueEntity);
                    return skuSaleAttrValueEntity;
                }).collect(Collectors.toList());
                skuSaleAttrValueService.saveBatch(skuSaleAttrValueEntities);


                //6.4、sku的优惠、满减等信息`gulimall_sms`->`sms_sku_ladder`/`sms_sku_full_reduction`/`sms_member_price`
                SkuReductionTo skuReductionTo = new SkuReductionTo();
                BeanUtils.copyProperties(item, skuReductionTo);
                skuReductionTo.setSkuId(infoEntity.getId());
                if (skuReductionTo.getFullCount() > 0 || skuReductionTo.getFullPrice().compareTo(BigDecimal.ZERO) > 0){
                    R r1 = couponFeignClient.saveSkuReduction(skuReductionTo);
                    if (r1.getCode() != 0){
                        log.error("远程保存优惠信息失败");
                    }
                }
            });
        }
    }

}