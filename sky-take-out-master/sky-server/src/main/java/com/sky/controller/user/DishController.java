package com.sky.controller.user;

import com.sky.constant.RedisConstent;
import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController("userDishController")
@RequestMapping("/user/dish")
@Slf4j
@Api(tags = "C端-菜品浏览接口")
public class DishController {
    @Autowired
    private DishService dishService;
    @Autowired
    private RedisTemplate redisTemplate;
    /**
     * 根据分类id查询菜品
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询菜品")
    public Result<List<DishVO>> list(Long categoryId) {
        //从redis中获取菜品列表
        String key = RedisConstent.USER_DISH_LIST_KEY_PREFIX + categoryId;//dish_id
        List<DishVO> list = (List<DishVO>) redisTemplate.opsForValue().get(key);
        //如果redis有数据，直接返回
        if(list != null && list.size() > 0){
            log.info("从redis中获取菜品列表");
            return Result.success(list);
        }
        //如果redis没有数据，从数据库中查询,并将数据存入redis
        Dish dish = Dish.builder()
            .categoryId(categoryId)
            .status(StatusConstant.ENABLE).build();

        list = dishService.listWithFlavor(dish);
        redisTemplate.opsForValue().set(key,list);

        return Result.success(list);
    }
}