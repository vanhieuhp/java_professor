package dev.hieunv.trigram.function;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomFunctionsContributor implements FunctionContributor {

    public static final String TRGM_WORD_SIMILARITY = "trgm_word_similarity";
    private static final Logger LOG = LoggerFactory.getLogger(CustomFunctionsContributor.class);

    @Override
    public void contributeFunctions(FunctionContributions functionContributors) {
        TrgmWordSimilaritySQLFunction function = new TrgmWordSimilaritySQLFunction(TRGM_WORD_SIMILARITY);
        functionContributors.getFunctionRegistry().register(TRGM_WORD_SIMILARITY, function);
        LOG.info("Registering function '{}' with SQL function '{}'", TRGM_WORD_SIMILARITY, function);
    }
}
