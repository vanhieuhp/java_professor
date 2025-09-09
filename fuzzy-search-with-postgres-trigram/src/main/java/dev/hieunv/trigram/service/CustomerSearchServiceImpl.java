package dev.hieunv.trigram.service;

import dev.hieunv.trigram.dto.CustomerDto;
import dev.hieunv.trigram.dto.PagedResult;
import dev.hieunv.trigram.entity.CustomerEntity;
import dev.hieunv.trigram.function.CustomFunctionsContributor;
import dev.hieunv.trigram.mapping.CustomerMapping;
import dev.hieunv.trigram.repo.CustomerEntityRepository;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Service
public class CustomerSearchServiceImpl implements CustomerSearchService {

    private final CustomerEntityRepository customerRepository;

    @Override
    public PagedResult<CustomerDto> searchCustomers(String search, Pageable pageable) {
        Page<CustomerEntity> result = customerRepository.findAll(search, pageable);
        List<CustomerDto> dtoList = result.getContent().stream()
                .map(CustomerMapping::asCustomerDto)
                .toList();

        return CustomerMapping.asPagedResult(result, dtoList);
    }

    @Override
    public PagedResult<CustomerDto> searchCustomersByNativeQuery(String search, Pageable pageable) {
        Page<CustomerEntity> result = customerRepository.findAllByNativeQuery(search, pageable);
        List<CustomerDto> dtoList = result.getContent().stream()
                .map(CustomerMapping::asCustomerDto)
                .toList();
        return CustomerMapping.asPagedResult(result, dtoList);
    }

    @Override
    public PagedResult<CustomerDto> searchCustomersBySpecification(String search, Pageable pageable) {
        Page<CustomerEntity> result = customerRepository.findAll(toSpecification(search), pageable);
        List<CustomerDto> dtoList = result.getContent().stream()
                .map(CustomerMapping::asCustomerDto)
                .toList();
        return CustomerMapping.asPagedResult(result, dtoList);
    }

    private Specification<CustomerEntity> toSpecification(String search) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> searchPredicates = new ArrayList<>();

            List<Expression<String>> fields = List.of(
                    root.get("address").get("street"),
                    root.get("address").get("city"),
                    root.get("contactDetails").get("firstName"),
                    root.get("contactDetails").get("lastName")
            );

            for (Expression<String> field : fields) {
                Expression<Boolean> searchExpression = criteriaBuilder.function(
                        CustomFunctionsContributor.TRGM_WORD_SIMILARITY,
                        Boolean.class,
                        criteriaBuilder.lower(field),
                        criteriaBuilder.lower(criteriaBuilder.literal(search))
                );
                searchPredicates.add(criteriaBuilder.isTrue(searchExpression));
            }

            return criteriaBuilder.or(searchPredicates.toArray(new Predicate[0]));
        };
    }
}
