package com.sky.mapper;


import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.vo.DishItemVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SetmealDishMapper {
    /**
     * 根据菜品id查询套餐id
     */
    List<Long> getSetmealIdsByDishIds(List<Long> dishIds);

    /**
     * 批量插入套餐菜品关系数据
     * @param setmealDishes
     */
    void insertBatch(List<SetmealDish> setmealDishes);

    /**
     * 根据套餐id批量删除套餐菜品关系数据
     * @param setmealIds
     */
    void deleteBySetmealIds(List<Long> setmealIds);

    /**
     * 根据套餐id查询菜品id
     * @param id
     * @return
     */
    @Select("select * from setmeal_dish where setmeal_id = #{id}")
    List<SetmealDish> getDishesBySetmealId(Long id);


//    /**
//     * 动态条件查询套餐
//     * @param setmeal
//     * @return
//     */
//    List<Setmeal> list(Setmeal setmeal);

//    /**
//     * 根据套餐id查询菜品选项
//     * @param setmealId
//     * @return
//     */
//    @Select("select sd.name, sd.copies, d.image, d.description " +
//        "from setmeal_dish sd left join dish d on sd.dish_id = d.id " +
//        "where sd.setmeal_id = #{setmealId}")
//    List<DishItemVO> getDishItemBySetmealId(Long setmealId);
}
