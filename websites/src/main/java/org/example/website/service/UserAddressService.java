package org.example.website.service;

import lombok.RequiredArgsConstructor;
import org.example.website.entity.User;
import org.example.website.entity.UserAddress;
import org.example.website.repository.UserAddressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserAddressService {

    private final UserAddressRepository userAddressRepository;

    @Transactional
    public void updateOrCreateAddress(User user, int ranking, String fullAddress) {
        // 1. 查找該用戶對應 ranking 的現有地址
        List<UserAddress> existingAddresses = userAddressRepository.findByUserAndRanking(user, ranking);

        // 2. 如果傳入的值為空，則刪除該地址記錄
        if (fullAddress == null || fullAddress.trim().isEmpty()) {
            if (!existingAddresses.isEmpty()) {
                userAddressRepository.deleteAll(existingAddresses);
            }
        } else {
            // 3. 如果傳入的值不為空，則新增或更新
            UserAddress address;
            if (existingAddresses.isEmpty()) {
                address = new UserAddress();
                address.setUser(user);
                address.setRanking(ranking);
                // 默認使用用戶註冊時的姓名和手機號
                address.setReceiverName(user.getName());
                address.setContactPhone(user.getPhone());
            } else {
                address = existingAddresses.get(0);
            }

            address.setFullAddress(fullAddress.trim());
            userAddressRepository.save(address);
        }
    }
}