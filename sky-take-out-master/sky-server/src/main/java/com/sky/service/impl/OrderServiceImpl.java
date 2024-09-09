package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.BaiduConstant;
import com.sky.constant.MessageConstant;
import com.sky.constant.RoleConstant;
import com.sky.constant.ShopConstant;
import com.sky.constant.WebSocketConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.entity.User;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.mapper.UserMapper;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.HttpClientUtil;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private WebSocketServer webSocketServer;
    //百度地图api
    @Value("${sky.baidu.ak}")
    private String baiduAk;
    //商家地址
    @Value("${sky.shop.address}")
    private String shopAddress;
    /**
     * 用户下单
     *
     * @param ordersSubmitDTO
     * @return
     */
    @Transactional
    @Override
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        //处理各种业务异常(地址簿为空，购物车为空等)
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        //判断是否超出配送范围
        String address =
            addressBook.getProvinceName()+addressBook.getCityName()+ addressBook.getDistrictName() + addressBook.getDetail();
        if(isOutOfRange(address))
        {
            throw new OrderBusinessException(MessageConstant.ORDER_DELIVERY_OUT_OF_RANGE);
        }
        //获取当前用户的购物车数据
        ShoppingCart shoppingCart = new ShoppingCart();
        Long userId = BaseContext.getCurrentId();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
        if (shoppingCartList == null || shoppingCartList.size() == 0) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }
        //向订单表插入一条订单数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        // TODO: 2023/12/31 用时间戳生成订单号可靠吗
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setConsignee(addressBook.getConsignee());
        orders.setAddress(addressBook.getDetail());
        orders.setPhone(addressBook.getPhone());
        orders.setUserId(userId);
        orderMapper.insert(orders);
        //向订单详情表插入多条订单详情数据
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId());//设置订单id
            orderDetailList.add(orderDetail);
        }
        //批量插入到订单详情表
        orderDetailMapper.insertBatch(orderDetailList);
        //清空用户购物车
        shoppingCartMapper.deleteByUserId(userId);
        //封装返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
            .id(orders.getId())
            .orderNumber(orders.getNumber())
            .orderAmount(orders.getAmount())
            .orderTime(orders.getOrderTime()).build();

        return orderSubmitVO;
    }

    /**
     * 调用百度地图api判断是否超出配送范围
     * @param userAddress
     * @return
     */
    private boolean isOutOfRange(String userAddress) {
        Map map = new HashMap(){{
            put("address", shopAddress);
            put("output", "json");
            put("ak", baiduAk);
            }};
        //获取商店经纬度坐标
        String shopCOordinate = HttpClientUtil.doGet(BaiduConstant.BAIDU_API_GEOCODEING_URL, map);
        JSONObject response = JSONObject.parseObject(shopCOordinate);
        if(!response.getString("status").equals("0")){
            throw new OrderBusinessException(MessageConstant.BAIDU_API_ERROR);
        }
        //解析响应结果
        JSONObject location = response.getJSONObject("result").getJSONObject("location");
        //经纬度坐标
        String lat = location.getString("lat");
        String lng = location.getString("lng");
        String shopLngLat = lat + "," + lng;

        //获取用户经纬度坐标
        map.put("address",userAddress);
        String userCoordinate = HttpClientUtil.doGet(BaiduConstant.BAIDU_API_GEOCODEING_URL, map);
        response = JSONObject.parseObject(userCoordinate);
        if(!response.getString("status").equals("0")){
            throw new OrderBusinessException(MessageConstant.BAIDU_API_ERROR);
        }
        //解析响应结果
        location = response.getJSONObject("result").getJSONObject("location");
        //经纬度坐标
        lat = location.getString("lat");
        lng = location.getString("lng");
        String userLngLat = lat + "," + lng;

        map.put("origin", shopLngLat);
        map.put("destination", userLngLat);
        map.put("steps_info","0");
        //获取路线规划信息
        JSONObject directionLiteJson = JSON.parseObject(HttpClientUtil.doGet(BaiduConstant.BAIDU_API_DIRECTIONLITE_URL, map));
        if(!directionLiteJson.getString("status").equals("0")){
            throw new OrderBusinessException(MessageConstant.BAIDU_API_ERROR);
        }
        //解析响应结果
        JSONObject result = directionLiteJson.getJSONObject("result");
        //获取路线距离
        JSONArray jsonArray = result.getJSONArray("routes");
        Integer distance = jsonArray.getJSONObject(0).getInteger("distance");
        //判断距离是否超出配送范围
        if(distance > ShopConstant.DELIVERY_RANGE_METERS){
            return true;
        }
        return false;
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

//        //调用微信支付接口，生成预支付交易单
//        JSONObject jsonObject = weChatPayUtil.pay(
//            ordersPaymentDTO.getOrderNumber(), //商户订单号
//            new BigDecimal(0.01), //支付金额，单位 元
//            "苍穹外卖订单", //商品描述
//            user.getOpenid() //微信用户的openid
//        );
        // TODO: 2024/1/1 暂时跳过微信支付接口，直接生成预支付交易单,并直接执行支付成功操作
        JSONObject jsonObject = new JSONObject();//由于个人微信支付接口未开通，所以直接跳过微信支付接口
        paySuccess(ordersPaymentDTO.getOrderNumber());





        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
            .id(ordersDB.getId())
            .status(Orders.TO_BE_CONFIRMED)
            .payStatus(Orders.PAID)
            .checkoutTime(LocalDateTime.now())
            .build();

        orderMapper.update(orders);

        // TODO: 2024/1/1 暂时跳过微信支付接口，直接修改订单状态的代码
        //通过webSocket向客户端推送接单消息
        Map map = new HashMap(){{
            put("type", WebSocketConstant.NEW_ORDER_TYPE);
            put("orderId", ordersDB.getId());
            put("content", "您有新的订单,请及时处理,"+"订单号: "+outTradeNo);
            }};
        String json = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);


    }

    /**
     * 分页查询历史订单
     *
     * @param ordersPageQueryDTO
     * @param roleType
     * @return
     */
    @Override
    public PageResult pageQuery(OrdersPageQueryDTO ordersPageQueryDTO, Integer roleType) {
        //设置分页
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        if(roleType.equals(RoleConstant.USER)){
            //如果是用户查询订单，需要根据用户id查询
            ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        }
        //分页条件查询
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);
        //封装返回结果
        List<OrderVO> orderVOList = new ArrayList<>();
        Long total = 0L;
        if(page != null && page.size() > 0){
            //如果是管理员查询订单，需要补充otherDishes字段信息,否则补充orderDetailList字段信息
            if (roleType.equals(RoleConstant.ADMIN)) {
                addOtherDishes(page, orderVOList);
            } else {
                addOrderDetails(page, orderVOList);
            }
            total = page.getTotal();
        }
        return new PageResult(total, orderVOList);
    }

    /**
     * 补充订单详情字段信息
     * @param page
     * @param orderVOList
     */
    private void addOrderDetails(Page<Orders> page, List<OrderVO> orderVOList) {
        for (Orders orders : page) {
            Long orderId = orders.getId();
            //查询订单详情
            List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orderId);
            //封装订单详情
            OrderVO orderVO = new OrderVO();
            BeanUtils.copyProperties(orders, orderVO);
            orderVO.setOrderDetailList(orderDetailList);
            orderVOList.add(orderVO);
        }
    }

    /**
     * 补充订单otherDishes字段信息
     * @param page
     * @param orderVOList
     */
    private void addOtherDishes(Page<Orders> page, List<OrderVO> orderVOList) {
        for (Orders orders : page) {
            //封装订单基础信息
            OrderVO orderVO = new OrderVO();
            BeanUtils.copyProperties(orders, orderVO);
            //获取订单详情信息
            List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());
            //根据订单详情生成otherDishes字符串,并封装到orderVO中
            String otherDishes = getOtherDishes(orderDetailList);
            orderVO.setOrderDishes(otherDishes);
            orderVOList.add(orderVO);
        }
    }

    /**
     * 根据订单详情生成otherDishes字符串
     * @param orderDetailList
     * @return
     */
    private String getOtherDishes(List<OrderDetail> orderDetailList) {
        //根据订单详情生成otherDishes字符串
        List<String> otherDishesList = orderDetailList.stream().map(orderDetail -> orderDetail.getName() + "*" + orderDetail.getNumber()).collect(Collectors.toList());
        return String.join(";", otherDishesList);
    }

    /**
     * 查询订单详情页
     *
     * @param id
     * @return
     */
    @Override
    public OrderVO details(Long id) {
        //根据id查询订单
        Orders orders = orderMapper.getById(id);
        //根据订单id查询订单详情
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
        //封装返回结果
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailList);
        return orderVO;
    }

    /**
     * 用户取消订单
     *
     * @param id
     */
    @Override
    public void userCancelById(Long id) {
        //根据id查询订单
        Orders ordersDB = orderMapper.getById(id);
        //判断订单是否存在
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        //如果订单状态到达商家接单以后的阶段，不能直接取消订单
        if (ordersDB.getStatus() >= Orders.CONFIRMED) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        // TODO: 2024/1/1 暂时跳过微信退款接口，直接修改订单状态的代码
        log.warn("跳过了微信退款功能");
//        weChatRefundVerify(ordersDB);
        //修改订单状态为已取消,并设置取消原因和取消时间
        Orders orders = Orders.builder()
            .id(id)
            .status(Orders.CANCELLED)
            .cancelReason(MessageConstant.ORDER_CANCELLED_BY_USER)
            .cancelTime(LocalDateTime.now())
            .payStatus(ordersDB.getPayStatus())
            .build();
        //更新订单
        orderMapper.update(orders);
    }

    /**
     * 再来一单
     *
     * @param id
     */
    @Override
    public void repetition(Long id) {
        //根据id查询订单详情
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
        //查询用户id
        Long userId = BaseContext.getCurrentId();
        //根据订单详情生成购物车数据
        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(orderDetail -> {
            ShoppingCart shoppingCart = new ShoppingCart();
            //将除了id,createTime以外的属性拷贝到shoppingCart对象中
            BeanUtils.copyProperties(orderDetail, shoppingCart, "id", "createTime");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());
            return shoppingCart;
        }).collect(Collectors.toList());
        //批量插入购物车数据
        shoppingCartMapper.insertBatch(shoppingCartList);
    }

    /**
     * 订单统计
     *
     * @return
     */
    @Override
    public OrderStatisticsVO statistics() {
        //分别查询待接单,待派送,派送中的订单数量
        Integer toBeConfirmed = orderMapper.countByStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.countByStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countByStatus(Orders.DELIVERY_IN_PROGRESS);
        //封装返回结果
        return OrderStatisticsVO.builder()
            .toBeConfirmed(toBeConfirmed)
            .confirmed(confirmed)
            .deliveryInProgress(deliveryInProgress).build();
    }

    /**
     * 商家确认订单
     *
     * @param ordersConfirmDTO
     */
    @Override
    public void adminConfirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orders = Orders.builder()
            .id(ordersConfirmDTO.getId())
            .status(Orders.CONFIRMED)
            .build();
        orderMapper.update(orders);
    }

    /**
     * 商家拒单
     *
     * @param ordersRejectionDTO
     */
    @Override
    public void adminRejection(OrdersRejectionDTO ordersRejectionDTO) {
        //判断有没有填写拒单原因
        if(ordersRejectionDTO.getRejectionReason().isEmpty()){
            throw new OrderBusinessException(MessageConstant.ORDER_REJECTION_REASON_IS_NULL);
        }
        Orders ordersDB = orderMapper.getById(ordersRejectionDTO.getId());
        //只有订单为待接单才能执行拒单操作
        if(!ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        // TODO: 2024/1/2 暂时跳过微信退款接口，直接修改订单状态的代码
        log.warn("跳过了微信退款功能");
//        weChatRefundVerify(ordersDB);
        //修改订单状态为已取消,并设置取消原因和取消时间
        Orders orders = Orders.builder()
            .id(ordersRejectionDTO.getId())
            .status(Orders.CANCELLED)
            .rejectionReason(ordersRejectionDTO.getRejectionReason())
            .cancelTime(LocalDateTime.now())
            .payStatus(ordersDB.getPayStatus())
            .build();
        orderMapper.update(orders);
    }


    /**
     * 商家取消订单
     *
     * @param ordersCancelDTO
     */
    @Override
    public void adminCancel(OrdersCancelDTO ordersCancelDTO) {
        //根据id查询订单
        Orders ordersDB = orderMapper.getById(ordersCancelDTO.getId());
        // TODO: 2024/1/2 暂时跳过微信退款接口，直接修改订单状态的代码
        log.warn("跳过了微信退款功能");
//        weChatRefundVerify(ordersDB);
        //修改订单状态为已取消,并设置取消原因和取消时间
        Orders orders = Orders.builder()
            .id(ordersCancelDTO.getId())
            .status(Orders.CANCELLED)
            .cancelReason(ordersCancelDTO.getCancelReason())
            .cancelTime(LocalDateTime.now())
            .payStatus(ordersDB.getPayStatus())
            .build();
        orderMapper.update(orders);
    }

    /**
     * 订单派送
     *
     * @param id
     */
    @Override
    public void delivery(Long id) {
        //根据id查询订单
        Orders ordersDB = orderMapper.getById(id);
        //只有订单为待派送才能执行派送操作
        if(ordersDB == null || !ordersDB.getStatus().equals(Orders.CONFIRMED)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders = Orders.builder()
            .id(id)
            .status(Orders.DELIVERY_IN_PROGRESS)
            .build();
        orderMapper.update(orders);
    }
    /**
     * 完成订单
     *
     * @param id
     */
    public void complete(Long id) {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在，并且状态为4
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders = Orders.builder()
            .id(id)
            .status(Orders.COMPLETED)
            .build();
        orderMapper.update(orders);
    }

    /**
     * 用户催单
     * @param id
     */
    @Override
    public void reminder(Long id) {
        //根据id查询订单
        Orders ordersDB = orderMapper.getById(id);
        //只有订单存在才能执行催单操作
        if(ordersDB == null){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        //通过webSocket向客户端推送催单消息
        Map map = new HashMap(){{
            put("type", WebSocketConstant.REMIND_ORDER_TYPE);
            put("orderId", ordersDB.getId());
            put("content", "用户催单,请及时处理,"+"订单号: "+ordersDB.getNumber());
            }};
        String json = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);
    }

    /**
     * 校验是否应当退款并处理退款
     * @param ordersDB
     */
    private void weChatRefundVerify(Orders ordersDB) {
        //如果订单支付状态为已支付，需要执行退款操作
        if(ordersDB.getPayStatus().equals(Orders.PAID)){
            try {
                String refund = weChatPayUtil.refund(
                    ordersDB.getNumber(),//商户订单号
                    ordersDB.getNumber(),//商户退款单号
                    ordersDB.getAmount(),//订单金额
                    ordersDB.getAmount()//退款金额
                );
                log.info("商家处理微信退款：{}", refund);
            } catch (Exception e) {
                throw new OrderBusinessException(MessageConstant.ORDER_REFUND_ERROR);
            }
            //修改订单支付状态为退款
            ordersDB.setPayStatus(Orders.REFUND);
        }
    }
}
