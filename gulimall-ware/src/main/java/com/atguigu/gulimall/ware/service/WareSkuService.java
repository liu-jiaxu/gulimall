package com.atguigu.gulimall.ware.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gulimall.common.utils.PageUtils;
import com.atguigu.gulimall.ware.entity.WareSkuEntity;

import java.util.List;
import java.util.Map;

/**
 * 商品库存
 *
 * @author liujiaxu
 * @email 15866718620@163.com
 * @date 2026-08-24 20:18:03
 */
public interface WareSkuService extends IService<WareSkuEntity> {

    PageUtils queryPage(Map<String, Object> params);

    /**
     * 入库：该 sku 在该仓库已有记录就累加库存，没有就新增一条
     *
     * @param skuId   sku id
     * @param wareId  仓库 id
     * @param skuName sku 名称，可为空
     * @param skuNum  本次入库数量
     */
    void addStock(Long skuId, Long wareId, String skuName, Integer skuNum);

    Map<Long, Boolean> getSkuHasStock(List<Long> skuIds);
}

