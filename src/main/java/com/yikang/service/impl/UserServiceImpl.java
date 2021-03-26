package com.yikang.service.impl;

import com.yikang.dao.UserDOMapper;
import com.yikang.dao.UserPasswordDOMapper;
import com.yikang.dataobject.UserDO;
import com.yikang.dataobject.UserPasswordDO;
import com.yikang.error.BusinessException;
import com.yikang.service.UserService;
import com.yikang.service.model.UserModel;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserDOMapper userDOMapper;

    @Autowired
    private UserPasswordDOMapper userPasswordDOMapper;

    @Override
    public UserModel getUserById(Integer id) {
        UserDO userDO = userDOMapper.selectByPrimaryKey(id);
        UserPasswordDO userPasswordDO = userPasswordDOMapper.selectByUserId(id);
        UserModel userModel = convertFromDataObject(userDO, userPasswordDO);
        return userModel;
    }

    @Override
    public void register(UserModel userModel) throws BusinessException {

    }

    @Override
    public UserModel validateLogin(String telphone, String encryptPassword) throws BusinessException {
        return null;
    }

    //DO是与数据库一一对应的关系，但是实际的模型数据可能包含多张表的属性，因此需要将多个DO转化成Model
    private UserModel convertFromDataObject(UserDO userDO, UserPasswordDO userPasswordDO) {
        if (userDO == null) {
            return null;
        }
        UserModel userModel = new UserModel();
        BeanUtils.copyProperties(userDO, userModel);
        if (userPasswordDO != null) {
            userModel.setEncrptPassword(userPasswordDO.getEncrptPassword());
        }
        return userModel;
    }
}
