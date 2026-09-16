package com.atguigu.gulimall.ware.service.impl;

import com.atguigu.gulimall.common.utils.PageUtils;
import com.atguigu.gulimall.common.utils.Query;
import com.atguigu.gulimall.ware.dao.WareSkuDao;
import com.atguigu.gulimall.ware.entity.WareSkuEntity;
import com.atguigu.gulimall.ware.service.WareSkuService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mysql.cj.util.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service("wareSkuService")
public class WareSkuServiceImpl extends ServiceImpl<WareSkuDao, WareSkuEntity> implements WareSkuService {

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        QueryWrapper<WareSkuEntity> wrapper = new QueryWrapper<>();

        String wareId = (String) params.get("wareId");
        if (!StringUtils.isNullOrEmpty(wareId)) {
            wrapper.eq("ware_id", wareId);
        }

        String skuId = (String) params.get("skuId");
        if (!StringUtils.isNullOrEmpty(skuId)) {
            wrapper.eq("sku_id", skuId);
        }

        IPage<WareSkuEntity> page = this.page(
                new Query<WareSkuEntity>().getPage(params),
                wrapper
        );

        return new PageUtils(page);
    }

    @Override
    public void addStock(Long skuId, Long wareId, String skuName, Integer skuNum) {
        // wms_ware_sku 上 (sku_id, ware_id) 只有普通索引、没有唯一约束，
        // 用 selectOne 一旦出现重复行会抛 TooManyResultsException，所以取第一条即可
        List<WareSkuEntity> exist = this.baseMapper.selectList(new QueryWrapper<WareSkuEntity>()
                .eq("sku_id", skuId)
                .eq("ware_id", wareId));
        WareSkuEntity wareSkuEntity = exist.isEmpty() ? null : exist.get(0);

        if (wareSkuEntity == null) {
            // 新增
            wareSkuEntity = new WareSkuEntity();
            wareSkuEntity.setStock(skuNum);
            wareSkuEntity.setStockLocked(0);
        } else {
            // 累加；老数据 stock 可能为 null
            Integer stock = wareSkuEntity.getStock();
            wareSkuEntity.setStock(stock == null ? skuNum : stock + skuNum);
        }

        // skuName 远程获取失败时是空串，别把库里已有的名字覆盖掉
        if (!StringUtils.isNullOrEmpty(skuName)) {
            wareSkuEntity.setSkuName(skuName);
        }
        wareSkuEntity.setWareId(wareId);
        wareSkuEntity.setSkuId(skuId);

        this.saveOrUpdate(wareSkuEntity);
    }

    /**
     * 查询sku是否有库存
     *
     * @param skuIds
     * @return
     */
    @Override
    public Map<Long, Boolean> getSkuHasStock(List<Long> skuIds) {
        return skuIds.stream()
                .collect(Collectors.toMap(
                        sku -> sku,
                        skuId -> this.baseMapper.exists(new QueryWrapper<WareSkuEntity>()
                                .eq("sku_id", skuId)
//                                .ge("stock", 0)
                                // 要计算当前库存减去锁定库存（其它用户已经预定的库存数） > 0的记录
                                .apply("stock - stock_locked > 0")
                        )));
    }

}