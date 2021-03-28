package com.yikang.service.impl;

import com.yikang.dao.UserDOMapper;
import com.yikang.dao.UserPasswordDOMapper;
import com.yikang.dataobject.UserDO;
import com.yikang.dataobject.UserPasswordDO;
import com.yikang.error.BusinessException;
import com.yikang.error.EmBusinessError;
import com.yikang.service.UserService;
import com.yikang.service.model.UserModel;
import com.yikang.validator.ValidationResult;
import com.yikang.validator.ValidatorImpl;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    @Autowired
    private UserDOMapper userDOMapper;

    @Autowired
    private UserPasswordDOMapper userPasswordDOMapper;

    @Autowired
    private ValidatorImpl validator;

    @Override
    public UserModel getUserById(Integer id) {
        UserDO userDO = userDOMapper.selectByPrimaryKey(id);
        UserPasswordDO userPasswordDO = userPasswordDOMapper.selectByUserId(id);
        UserModel userModel = convertFromDataObject(userDO, userPasswordDO);
        return userModel;
    }

    @Override
    @Transactional
    public void register(UserModel userModel) throws BusinessException {
        if (userModel == null) {
            logger.error("userModel为null");
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "userModel为null");
        }
        ValidationResult result = validator.validate(userModel);
        if (result.isHasErrors()){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, result.getErrMsg());
        }
        UserDO userDO = convertFromModel(userModel);
        try {
            //需要调用dao层的方法，因此需要转成data object
            userDOMapper.insertSelective(userDO);
            UserPasswordDO userPasswordDO = convertPasswordFromModel(userModel);
            userPasswordDO.setUserId(userDO.getId());
            userPasswordDOMapper.insertSelective(userPasswordDO);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "手机号重复");
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR);
        }
    }

    @Override
    public UserModel validateLogin(String telphone, String encryptPassword) throws BusinessException {
        UserDO userDO = userDOMapper.selectByTelphone(telphone);
        if (userDO == null) {
            throw new BusinessException(EmBusinessError.USER_LOGIN_FAIL);
        }
        UserPasswordDO userPasswordDO = userPasswordDOMapper.selectByUserId(userDO.getId());
        UserModel userModel = convertFromDataObject(userDO, userPasswordDO);
        if (!StringUtils.equals(encryptPassword, userModel.getEncrptPassword())) {
            throw new BusinessException(EmBusinessError.USER_LOGIN_FAIL);
        }
        return userModel;
    }

    private UserPasswordDO convertPasswordFromModel(UserModel userModel) {
        if (userModel == null) return null;
        UserPasswordDO userPasswordDO = new UserPasswordDO();
        userPasswordDO.setEncrptPassword(userModel.getEncrptPassword());
        userPasswordDO.setUserId(userModel.getId());
        return userPasswordDO;
    }

    private UserDO convertFromModel(UserModel userModel) {
        if (userModel == null) return null;
        UserDO userDo = new UserDO();
        BeanUtils.copyProperties(userModel, userDo);
        return userDo;
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
