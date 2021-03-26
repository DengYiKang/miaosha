package com.yikang.service;


import com.yikang.error.BuinessException;
import com.yikang.service.model.UserModel;

public interface UserService {
    UserModel getUserById(Integer id);

    void register(UserModel userModel) throws BuinessException;

    /*
     * telphone：用户注册手机号
     * password：用户加密后的密码
     * */
    UserModel validateLogin(String telphone, String encryptPassword) throws BuinessException;
}
