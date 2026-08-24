package com.atguigu.gulimall;

import com.atguigu.gulimall.product.entity.BrandEntity;
import com.atguigu.gulimall.product.service.BrandService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class GulimallProductApplicationTests {

    @Autowired
    BrandService brandService;

    @Test
    void contextLoads() {
        BrandEntity brandEntity = new BrandEntity();
        brandEntity.setDescript("这是测试数据2");
        brandEntity.setName("oppo");
        boolean save = brandService.save(brandEntity);
        System.out.println(save);

        List<BrandEntity> list = brandService.list(
                new LambdaQueryWrapper<BrandEntity>().eq(BrandEntity::getBrandId, 1L));
        list.forEach(System.out::println);
    }

}
