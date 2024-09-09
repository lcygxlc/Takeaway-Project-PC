package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.exception.SetmealEnableFailedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class SetmealServiceImpl implements SetmealService{
    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    @Autowired
    private DishMapper dishMapper;
    /**
     * 新增套餐及其对应菜品关系数据
     *
     * @param setmealDTO
     */
    @Transactional
    @Override
    public void saveWithDish(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO,setmeal);
        //向套餐表插入一条数据
        setmealMapper.insert(setmeal);
        //向套餐菜品关系表插入多条数据
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        //判断是否有菜品数据,并将套餐id填充
        if(setmealDishes != null && setmealDishes.size() > 0){
            setmealDishes.forEach(dish -> dish.setSetmealId(setmeal.getId()));
            //批量插入
            setmealDishMapper.insertBatch(setmealDishes);
        }
    }

    /**
     * 分页查询套餐
     *
     * @param setmealPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        PageHelper.startPage(setmealPageQueryDTO.getPage(),setmealPageQueryDTO.getPageSize());
        Page<SetmealVO> page = setmealMapper.pageQuery(setmealPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());

    }

    /**
     * 根据id批量删除套餐
     *
     * @param ids
     */
    @Transactional
    @Override
    public void deleteBatch(List<Long> ids) {
        //判断当前套餐是否启售中,如果启售中则不能删除
        for(Long id : ids){
            Setmeal setmeal = setmealMapper.getById(id);
            if(Objects.equals(setmeal.getStatus(), StatusConstant.ENABLE)){
                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
            }
        }
        //删除套餐表中对应数据
        setmealMapper.deleteByIds(ids);
        //删除套餐菜品关系表中对应数据
        setmealDishMapper.deleteBySetmealIds(ids);
    }

    /**
     * 根据id查询套餐
     *
     * @param id
     * @return
     */
    @Override
    public SetmealVO getById(Long id) {
        //获取套餐基础信息
        SetmealVO setmealVO = setmealMapper.getByIdWithCategoryName(id);
        //根据套餐id获取对应菜品信息
        List<SetmealDish> setmealDishes = setmealDishMapper.getDishesBySetmealId(id);
        setmealVO.setSetmealDishes(setmealDishes);
        return setmealVO;
    }

    /**
     * 修改套餐
     *
     * @param setmealDTO
     */
    @Override
    public void updateWithDish(SetmealDTO setmealDTO) {
        //修改套餐基础信息
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        setmealMapper.update(setmeal);
        //删除套餐菜品关系表中对应数据
        setmealDishMapper.deleteBySetmealIds(Arrays.asList(setmeal.getId()));
        //向套餐菜品关系表插入多条数据
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        // TODO: 2023/12/23 将菜品数据批量填充的方法代码有重复,等待优化方案 
        //判断是否有菜品数据,并将套餐id填充
        if (setmealDishes != null && setmealDishes.size() > 0) {
            setmealDishes.forEach(dish -> dish.setSetmealId(setmeal.getId()));
            //批量插入
            setmealDishMapper.insertBatch(setmealDishes);
        }
        if(Objects.equals(setmeal.getStatus(), StatusConstant.ENABLE)){
            try {
                //调用启用套餐方法检查是否能启用
                startOrStop(StatusConstant.ENABLE, setmeal.getId());
            } catch (SetmealEnableFailedException e) {
                //如果启用失败,则将套餐状态改为禁用
                log.warn(MessageConstant.SETMEAL_ENABLE_FAILED);
                startOrStop(StatusConstant.DISABLE, setmeal.getId());
            }

        }

    }

    /**
     * 启用或禁用套餐
     *
     * @param status
     * @param id
     */
    @Override
    public void startOrStop(Integer status, Long id) {
        if(Objects.equals(status, StatusConstant.ENABLE)){

            List<Dish> dishList = dishMapper.getBySetmealId(id);
            if(dishList != null && dishList.size() > 0){
                dishList.forEach(dish -> {
                    if(StatusConstant.DISABLE.equals(dish.getStatus())){
                        throw new SetmealEnableFailedException(MessageConstant.SETMEAL_ENABLE_FAILED);
                    }
                });
            }
        }

        Setmeal setmeal = Setmeal.builder()
            .id(id)
            .status(status)
            .build();
        setmealMapper.update(setmeal);
    }
    /**
     * 条件查询
     * @param setmeal
     * @return
     */
    public List<Setmeal> list(Setmeal setmeal) {
        List<Setmeal> list = setmealMapper.list(setmeal);
        return list;
    }

    /**
     * 根据id查询菜品选项
     * @param id
     * @return
     */
    public List<DishItemVO> getDishItemById(Long id) {
        return setmealMapper.getDishItemBySetmealId(id);
    }
}
