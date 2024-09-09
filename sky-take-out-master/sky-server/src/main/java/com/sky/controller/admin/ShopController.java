package com.sky.controller.admin;

import com.sky.constant.RedisConstent;
import com.sky.constant.ShopConstant;
import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("adminShopController")
@RequestMapping("/admin/shop")
@Api(tags = "商铺管理接口")
@Slf4j
public class ShopController {
    @Autowired
    private RedisTemplate<String, Integer> redisTemplate;

    /**
     * 设置商铺营业状态
     * @param status
     * @return
     */
    @PutMapping("/{status}")
    @ApiOperation("设置商铺营业状态")
    public Result setStatus(@PathVariable Integer status){
        log.info("设置商铺营业状态：{}",status == ShopConstant.ENABLE ? "营业中" : "休息中");
        redisTemplate.opsForValue().set(RedisConstent.SHOP_STATUS_KEY,status);
        return Result.success();
    }

    /**
     * 获取商铺营业状态
     * @return
     */
    @GetMapping("/status")
    @ApiOperation("获取商铺营业状态")
    public Result<Integer> getStatus(){
        Integer status = redisTemplate.opsForValue().get(RedisConstent.SHOP_STATUS_KEY);
        log.info("获取商铺营业状态：{}", status.equals(ShopConstant.ENABLE) ? "营业中" : "休息中");
        return Result.success(status);
    }

}
