package dev.hieunv.aspect;

import dev.hieunv.repository.CardInfoRepository;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("${aspect.cardRemove.enabled:false}")
public class CardRemoveAspect {

    private final CardInfoRepository repository;

    @Pointcut("execution(* dev.hieunv.controller.CardController.getCard(..)) && args(id)")
    public void cardController(String id) {
    }

    @AfterReturning(value = "cardController(id)", argNames = "id")
    public void deleteCard(String id) {
        repository.deleteById(id);
    }
}
