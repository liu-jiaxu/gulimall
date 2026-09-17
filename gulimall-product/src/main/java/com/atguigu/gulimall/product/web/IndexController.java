package com.atguigu.gulimall.product.web;

import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.atguigu.gulimall.product.service.CategoryService;
import com.atguigu.gulimall.product.vo.Catelog2Vo;
import lombok.Builder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

/**
 * @ClassName: IndexController
 * @Package: com.atguigu.gulimall.product.web
 * @Description:
 * @Author: 刘家旭
 * @Create: 2026/9/17 - 09:41
 * @Version: v1.0
 */
@Controller
@Builder
public class IndexController {

    private CategoryService categoryService;

    @GetMapping({"/", "/index.html"})
    public String indexPage(Model model) {

        // 1.查询一级分类
        List<CategoryEntity> categoryEntityList = categoryService.getLevel1CategoryList();

        model.addAttribute("category_list", categoryEntityList);
        // prefix前缀classpath:/templates/ suffix后缀为.html
        // 视图解析器自动拼接
        return "index";
    }

    // index/catalog.json
    @ResponseBody
    @GetMapping("index/catalog.json")
    public Map<String, List<Catelog2Vo>> getCatelogJson() {
        return categoryService.getCatelogJson();
    }

}
