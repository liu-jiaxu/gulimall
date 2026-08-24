package com.atguigu.gulimall.order.dao;

import com.atguigu.gulimall.order.entity.OrderEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单
 * 
 * @author liujiaxu
 * @email 15866718620@163.com
 * @date 2026-08-24 20:07:07
 */
@Mapper
public interface OrderDao extends BaseMapper<OrderEntity> {
	
}
