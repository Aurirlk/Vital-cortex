package cn.kmbeast.service.impl;

import cn.kmbeast.mapper.PatientProfileMapper;
import cn.kmbeast.pojo.entity.PatientProfile;
import cn.kmbeast.service.PatientProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户画像服务实现类
 */
@Slf4j
@Service
public class PatientProfileServiceImpl implements PatientProfileService {

    @Autowired
    private PatientProfileMapper patientProfileMapper;

    @Override
    public PatientProfile getByUserId(Integer userId) {
        if (userId == null) {
            return null;
        }
        return patientProfileMapper.getByUserId(userId);
    }

    @Override
    @Transactional
    public void saveOrUpdate(PatientProfile patientProfile) {
        if (patientProfile == null || patientProfile.getUserId() == null) {
            return;
        }
        
        PatientProfile existing = patientProfileMapper.getByUserId(patientProfile.getUserId());
        
        if (existing == null) {
            // 插入新记录
            patientProfile.setCreateTime(LocalDateTime.now());
            patientProfile.setLastUpdateTime(LocalDateTime.now());
            patientProfileMapper.insert(patientProfile);
            log.info("创建用户画像: userId={}", patientProfile.getUserId());
        } else {
            // 更新现有记录
            patientProfile.setLastUpdateTime(LocalDateTime.now());
            patientProfileMapper.update(patientProfile);
            log.info("更新用户画像: userId={}", patientProfile.getUserId());
        }
    }

    @Override
    @Transactional
    public void updateBasicInfo(Integer userId, String gender, Integer age, String birthDate) {
        if (userId == null) {
            return;
        }
        
        PatientProfile profile = getOrCreateProfile(userId);
        
        if (gender != null && !gender.isEmpty()) {
            profile.setGender(gender);
        }
        if (age != null) {
            profile.setAge(age);
        }
        if (birthDate != null && !birthDate.isEmpty()) {
            try {
                profile.setBirthDate(LocalDate.parse(birthDate));
            } catch (Exception e) {
                log.warn("日期格式错误: {}", birthDate);
            }
        }
        
        profile.setLastUpdateTime(LocalDateTime.now());
        patientProfileMapper.update(profile);
        log.info("更新用户基本信息: userId={}", userId);
    }

    @Override
    @Transactional
    public void updateBodyMetrics(Integer userId, Double height, Double weight) {
        if (userId == null) {
            return;
        }
        
        PatientProfile profile = getOrCreateProfile(userId);
        
        if (height != null) {
            profile.setHeight(height);
        }
        if (weight != null) {
            profile.setWeight(weight);
        }
        
        // 自动计算BMI
        profile.calculateBmi();
        
        profile.setLastUpdateTime(LocalDateTime.now());
        patientProfileMapper.update(profile);
        log.info("更新用户身体指标: userId={}, height={}, weight={}, bmi={}", 
            userId, height, weight, profile.getBmi());
    }

    @Override
    @Transactional
    public void updateMedicalHistory(Integer userId, String chronicDiseases, String allergies, String medications) {
        if (userId == null) {
            return;
        }
        
        PatientProfile profile = getOrCreateProfile(userId);
        
        if (chronicDiseases != null) {
            profile.setChronicDiseases(chronicDiseases);
        }
        if (allergies != null) {
            profile.setAllergies(allergies);
        }
        if (medications != null) {
            profile.setMedications(medications);
        }
        
        profile.setLastUpdateTime(LocalDateTime.now());
        patientProfileMapper.update(profile);
        log.info("更新用户疾病史: userId={}", userId);
    }

    @Override
    @Transactional
    public void updatePublicIndicator(Integer userId, String symbol, String value) {
        if (userId == null || symbol == null || value == null) {
            return;
        }
        
        PatientProfile profile = getOrCreateProfile(userId);
        
        // 根据符号更新对应的公共指标字段
        profile.updatePublicIndicator(symbol, value);
        
        profile.setLastUpdateTime(LocalDateTime.now());
        patientProfileMapper.update(profile);
        log.info("更新用户公共指标: userId={}, symbol={}, value={}", userId, symbol, value);
    }

    @Override
    @Transactional
    public void delete(Integer userId) {
        if (userId == null) {
            return;
        }
        patientProfileMapper.deleteByUserId(userId);
        log.info("删除用户画像: userId={}", userId);
    }

    /**
     * 获取或创建用户画像
     */
    private PatientProfile getOrCreateProfile(Integer userId) {
        PatientProfile profile = patientProfileMapper.getByUserId(userId);
        if (profile == null) {
            profile = PatientProfile.builder()
                    .userId(userId)
                    .createTime(LocalDateTime.now())
                    .lastUpdateTime(LocalDateTime.now())
                    .build();
            patientProfileMapper.insert(profile);
            log.info("自动创建用户画像: userId={}", userId);
        }
        return profile;
    }
}
