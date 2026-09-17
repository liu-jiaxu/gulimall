package com.atguigu.gulimall.product.service.impl;

import com.atguigu.gulimall.common.utils.PageUtils;
import com.atguigu.gulimall.common.utils.Query;
import com.atguigu.gulimall.product.dao.CategoryBrandRelationDao;
import com.atguigu.gulimall.product.dao.CategoryDao;
import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.atguigu.gulimall.product.method.CategoryMethod;
import com.atguigu.gulimall.product.service.CategoryService;
import com.atguigu.gulimall.product.vo.Catelog2Vo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;


@Service("categoryService")
public class CategoryServiceImpl extends ServiceImpl<CategoryDao, CategoryEntity> implements CategoryService {

    @Autowired
    private CategoryDao categoryDao;
    @Autowired
    private CategoryBrandRelationDao categoryBrandRelationDao;
    @Autowired
    private CategoryMethod categoryMethod;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<CategoryEntity> page = this.page(
                new Query<CategoryEntity>().getPage(params),
                new QueryWrapper<>()
        );

        return new PageUtils(page);
    }

    /**
     * 查询所有分类以及子分类，以树形结构组装起来
     *
     * @return 树形结构的分类列表
     */
    @Override
    public List<CategoryEntity> listWithTree() {
        // 1.查询所有分类
        List<CategoryEntity> allCategory = getAllCategory();
        // 2.组装成父子的树形结构
        // 2.1 找到所有的一级分类
        return allCategory.stream()
                .filter(category -> category.getParentCid() == 0L)
                // 2.2 递归查找子分类
                .peek(category -> category.setChildren(getChildren(category, allCategory)))
                // 2.3 按照sort字段进行排序
                .sorted(Comparator.comparingInt((CategoryEntity c) -> c.getSort() == null ? 0 : c.getSort()).reversed())
                .toList();
    }

    /**
     * 删除分类
     *
     * @param longs 要删除的分类ID列表
     */
    @Override
    public void removeMenusByIds(List<Long> longs) {
        // TODO 1.检查当前删除的菜单是否被别的地方引用

        // 2.如果未被引用，则执行逻辑删除操作
        categoryDao.deleteByIds(longs);

    }

    /**
     * 找到catelogId的完整路径 [父/子/孙]
     *
     * @param catelogId
     * @return
     */
    @Override
    public Long[] findCatelogPath(Long catelogId) {
        return categoryMethod.findCatelogPath(catelogId);
    }

    /**
     * 修改功能<br>
     * 因为存在品牌分类关联表，所以修改品牌信息需要级联更新<br>
     *
     * @param category 分类实体
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateDetails(CategoryEntity category) {
        // 1.更新当前分类
        this.updateById(category);
        // 2.级联更新品牌分类关联表中的分类名称
        if (StringUtils.isNotEmpty(category.getName())) {
            categoryBrandRelationDao.updateCatelogName(category.getCatId(), category.getName());
        }
    }

    /**
     * 查找所有一级分类
     *
     * @return
     */
    @Override
    public List<CategoryEntity> getLevel1CategoryList() {
        return baseMapper.selectList(
                new QueryWrapper<CategoryEntity>().eq("parent_cid", 0).eq("show_status", 1L));
    }

    @Override
    public Map<String, List<Catelog2Vo>> getCatelogJson() {

        // 这么写数据库炸了
        // 查询全部1级分类
//        List<CategoryEntity> level1CategoryList = getLevel1CategoryList();
//        return level1CategoryList.stream().collect(Collectors.toMap(
//                k -> k.getCatId().toString(),
//                v -> {
//                    // 查询2级分类
//                    List<CategoryEntity> level2CategoryList = baseMapper.selectList(
//                            new QueryWrapper<CategoryEntity>().eq("parent_cid", v.getCatId()));
//                    List<Catelog2Vo> catelog2Vos = new ArrayList<>();
//                    if (level2CategoryList != null) {
//                        catelog2Vos = level2CategoryList.stream().map(
//                                category2 -> {
//                                    Catelog2Vo catelog2Vo = new Catelog2Vo();
//                                    catelog2Vo.setCatalog1Id(v.getCatId().toString());
//                                    // 查询3级分类
//                                    List<CategoryEntity> level3CategoryList = baseMapper.selectList(
//                                            new QueryWrapper<CategoryEntity>().eq("parent_cid", category2.getCatId()));
//                                    List<Catelog2Vo.Catelog3Vo> catelog3Vos = null;
//                                    if (level3CategoryList != null) {
//                                        catelog3Vos = level3CategoryList.stream().map(
//                                                category3 -> {
//                                                    Catelog2Vo.Catelog3Vo catelog3Vo = new Catelog2Vo.Catelog3Vo();
//                                                    catelog3Vo.setCatalog2Id(category2.getCatId().toString());
//                                                    catelog3Vo.setId(category3.getCatId().toString());
//                                                    catelog3Vo.setName(category3.getName());
//                                                    return catelog3Vo;
//                                                }
//                                        ).toList();
//                                    }
//                                    catelog2Vo.setCatalog3List(catelog3Vos);
//                                    catelog2Vo.setId(category2.getCatId().toString());
//                                    catelog2Vo.setName(category2.getName());
//                                    return catelog2Vo;
//                                }
//                        ).toList();
//                    }
//                    return catelog2Vos;
//                }
//        ));

        // 1.查询全部数据
        List<CategoryEntity> allCategory = getAllCategory();
        if (allCategory == null || allCategory.isEmpty()) {
            return new HashMap<>();
        }
        
        // 2.数据封装
        return allCategory.stream()
                // 过滤出全部1级分类
                .filter(category -> category.getParentCid().equals(0L))
                // 数据封装
                .collect(Collectors.toMap(
                k -> k.getCatId().toString(),
                v -> {
                    // 无需额外考虑集合为null，toList流式操作没有匹配元素自动返回空集合
                    // 当前1级分类的全部2级分类
                    /*
                        getCategoryAllChildren还可以继续优化，当前子节点检索每次都是全表数据查询，
                    可以将全部数据按parent_cid分组存储到Map<String, List<CategoryEntity>>中，
                    这样每次直接检索map集合即可。此处数据量少可无需优化。
                     */
                    List<CategoryEntity> categorylevel2List = getCategoryAllChildren(v, allCategory);
                    return categorylevel2List.stream().map(
                            categorylevel2 -> {
                                Catelog2Vo catelog2Vo = new Catelog2Vo();
                                catelog2Vo.setCatalog1Id(v.getCatId().toString());
                                // 当前2级分类的全部3级分类
                                List<CategoryEntity> categorylevel3List = getCategoryAllChildren(categorylevel2, allCategory);
                                List<Catelog2Vo.Catelog3Vo> catelog3Vos = categorylevel3List.stream().map(
                                        categorylevel3 -> new Catelog2Vo.Catelog3Vo(
                                                categorylevel2.getCatId().toString(),
                                                categorylevel3.getCatId().toString(),
                                                categorylevel3.getName())
                                ).toList();
                                catelog2Vo.setCatalog3List(catelog3Vos);
                                catelog2Vo.setId(categorylevel2.getCatId().toString());
                                catelog2Vo.setName(categorylevel2.getName());
                                return catelog2Vo;
                            }
                    ).toList();
                }
        ));
    }

    /**
     * 查询所有分类
     * @return 所有分类
     */
    private List<CategoryEntity> getAllCategory() {
        return categoryDao.selectList(
                new LambdaQueryWrapper<CategoryEntity>().eq(CategoryEntity::getShowStatus, 1L));
    }

    /**
     * 查询当前分类的全部子分类
     * @param rootCategory 当前分类
     * @param allCategory 所有分类
     * @return 当前分类的全部子分类
     */
    private List<CategoryEntity> getCategoryAllChildren(CategoryEntity rootCategory, List<CategoryEntity> allCategory) {
        if (allCategory == null) {
            return new ArrayList<>();
        }
        return allCategory.stream()
                .filter(category -> Objects.equals(category.getParentCid(), rootCategory.getCatId()))
                .sorted(Comparator.comparingInt((CategoryEntity c) -> c.getSort() == null ? 0 : c.getSort()).reversed())
                .toList();
    }

    /**
     * 递归查找子分类
     *
     * @param rootCategory  子分类的根节点
     * @param allCategory 所有分类
     * @return 子分类列表
     */
    private List<CategoryEntity> getChildren(CategoryEntity rootCategory, List<CategoryEntity> allCategory) {
        if (allCategory == null) {
            return new ArrayList<>();
        }
        return allCategory.stream()
                .filter(category -> category.getParentCid().equals(rootCategory.getCatId()))
                .peek(category -> category.setChildren(getChildren(category, allCategory)))
                .sorted(Comparator.comparingInt((CategoryEntity c) -> c.getSort() == null ? 0 : c.getSort()).reversed())
                .toList();
    }

}