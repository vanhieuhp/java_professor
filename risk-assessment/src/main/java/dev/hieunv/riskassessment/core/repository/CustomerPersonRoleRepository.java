package dev.hieunv.riskassessment.core.repository;

import dev.hieunv.riskassessment.constant.RoleEnum;
import dev.hieunv.riskassessment.core.entity.CustomerPersonRole;
import dev.hieunv.riskassessment.core.entity.CustomerPersonRoleId;
import org.springframework.data.repository.Repository;

import java.util.List;

public interface CustomerPersonRoleRepository extends Repository<CustomerPersonRole, CustomerPersonRoleId> {

    List<CustomerPersonRole> findByCustomerId(String customerId);

    List<CustomerPersonRole> findByCustomerIdAndRole(String customerId, RoleEnum role);

    List<CustomerPersonRole> findByPersonId(Long personId);
}
