package com.sky.controller.admin;

import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
     * 菜品管理接口
     */
    @RestController
    @RequestMapping("/admin/dish")
    @Api(tags = "菜品管理接口")
    @Slf4j
    public class DishController {
        @Autowired
        private DishService dishService;
        @Autowired
        private RedisTemplate redisTemplate;

        /**
         * 新增菜品
         * @param dishDTO
         * @return
         */
        @PostMapping
        @ApiOperation("新增菜品")
        public Result save(@RequestBody DishDTO dishDTO){
            log.info("新增菜品：{}", dishDTO);
            dishService.saveWithFlavor(dishDTO);
            //清理单个分类缓存
            cleanCache("dish_"+dishDTO.getCategoryId());
            return Result.success();
        }

        /**
         * 分页查询菜品
         * @param dishPageQueryDTO
         * @return
         */
        @GetMapping("/page")
        @ApiOperation("分页查询菜品")
        public Result<PageResult> page(DishPageQueryDTO dishPageQueryDTO){
            log.info("分页查询菜品：{}", dishPageQueryDTO);
            PageResult pageResult = dishService.pageQuery(dishPageQueryDTO);
            return Result.success(pageResult);
        }

        /**
         * 根据id删除菜品
         * @param ids
         * @return
         */
        @DeleteMapping
        @ApiOperation("根据id批量删除菜品")
        public Result delete(@RequestParam List<Long> ids){
            log.info("根据id批量删除菜品：{}", ids);
            dishService.deleteBatch(ids);
            //清理redis缓存
            // TODO: 2023/12/30 有必要删除的时候清理缓存吗？毕竟删除的前提是禁用这个菜品,既然禁用了，那么就不会再被用户查询到了,等修改的时候再一并清理缓存不就好了吗？
            cleanCache("dish_*");
            return Result.success();
        }
        /**
         * 根据id查询菜品及对应口味数据
         * @param id
         * @return
         */
        @GetMapping("/{id}")
        @ApiOperation("根据id查询菜品")
        public Result<DishVO> getById(@PathVariable Long id){
            log.info("根据id查询菜品：{}", id);
            DishVO dishVO = dishService.getByIdWithFlavor(id);
            return Result.success(dishVO);
        }

    /**
     * 根据id修改菜品
     * @param dishDTO
     * @return
     */
    @PutMapping
    @ApiOperation("根据id修改菜品")
    public Result update(@RequestBody DishDTO dishDTO){
            log.info("修改菜品：{}", dishDTO);
            dishService.updateWithFlavor(dishDTO);
            //由于修改的时候涉及到的缓存更新机制比较复杂，所以这里简单粗暴的清理所有缓存
            cleanCache("dish_*");
            return Result.success();
    }

    /**
     * 根据菜品分类id查询菜品
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据菜品分类id查询菜品")
    public Result<List<Dish>> list(Long categoryId){
            List<Dish> dishList = dishService.listByCategoryId(categoryId);
            return Result.success(dishList);
    }

    /**
     * 启用或禁用菜品
     * @param status
     * @param id
     * @return
     */
    @PostMapping("/status/{status}")
    @ApiOperation("启用或禁用菜品")
    public Result status(@PathVariable Integer status,Long id){
            dishService.startOrStop(status,id);
            //同样的，这里也是简单粗暴的清理所有缓存
            cleanCache("dish_*");
            return Result.success();
    }

    /**
     * 清理缓存数据
     * @param pattern
     */
    private void cleanCache(String pattern){
        Set keys = redisTemplate.keys(pattern);
        redisTemplate.delete(keys);
    }
}