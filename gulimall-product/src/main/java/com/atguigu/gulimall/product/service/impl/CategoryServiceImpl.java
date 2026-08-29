package com.atguigu.gulimall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.product.dao.CategoryDao;
import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.atguigu.gulimall.product.service.CategoryService;


@Service("categoryService")
public class CategoryServiceImpl extends ServiceImpl<CategoryDao, CategoryEntity> implements CategoryService {

    @Autowired
    private CategoryDao categoryDao;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<CategoryEntity> page = this.page(
                new Query<CategoryEntity>().getPage(params),
                new QueryWrapper<CategoryEntity>()
        );

        return new PageUtils(page);
    }

    /**
     * 查询所有分类以及子分类，以树形结构组装起来
     * @return 树形结构的分类列表
     */
    @Override
    public List<CategoryEntity> listWithTree() {
        // 1.查询所有分类
        List<CategoryEntity> allCategories = categoryDao.selectList(
                new LambdaQueryWrapper<CategoryEntity>().eq(CategoryEntity::getShowStatus, 1L));
        // 2.组装成父子的树形结构
        // 2.1 找到所有的一级分类
        return allCategories.stream()
                .filter(category -> category.getParentCid() == 0L)
                // 2.2 递归查找子分类
                .peek(category -> category.setChildren(getChildren(category, allCategories)))
                // 2.3 按照sort字段进行排序
                .sorted(Comparator.comparingInt(c -> c.getSort() == null ? 0 : c.getSort()))
                .toList();
    }

    /**
     * 删除分类
     * @param longs 要删除的分类ID列表
     */
    @Override
    public void removeMenusByIds(List<Long> longs) {
        // TODO 1.检查当前删除的菜单是否被别的地方引用

        // 2.如果未被引用，则执行逻辑删除操作
        categoryDao.deleteByIds(longs);

    }

    /**
     * 递归查找子分类
     * @param rootCategory 子分类的根节点
     * @param allCategories 所有分类
     * @return 子分类列表
     */
    private List<CategoryEntity> getChildren(CategoryEntity rootCategory, List<CategoryEntity> allCategories) {
        return allCategories.stream()
                .filter(category -> category.getParentCid().equals(rootCategory.getCatId()))
                .peek(category -> category.setChildren(getChildren(category, allCategories)))
                .sorted(Comparator.comparingInt(c -> c.getSort() == null ? 0 : c.getSort()))
                .toList();
    }

}