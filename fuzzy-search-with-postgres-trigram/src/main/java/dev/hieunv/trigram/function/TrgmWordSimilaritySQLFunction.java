package dev.hieunv.trigram.function;

import org.hibernate.dialect.function.StandardSQLFunction;
import org.hibernate.query.ReturnableType;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.spi.SqlAppender;
import org.hibernate.sql.ast.tree.SqlAstNode;
import org.hibernate.type.BasicTypeReference;
import org.hibernate.type.SqlTypes;

import java.util.List;

public class TrgmWordSimilaritySQLFunction extends StandardSQLFunction {

    private static final BasicTypeReference<Boolean> RETURN_TYPE =
            new BasicTypeReference<>("boolean", Boolean.class, SqlTypes.BOOLEAN);

    public TrgmWordSimilaritySQLFunction(String functionName) {
        super(functionName, true, RETURN_TYPE);
    }

    @Override
    public void render(SqlAppender sqlAppender,
                       List<? extends SqlAstNode> sqlAstArguments,
                       ReturnableType<?> returnType,
                       SqlAstTranslator<?> walker) {
        if (sqlAstArguments.size() != 2) {
            throw new IllegalArgumentException("Function '" + getName() + "' requires exactly 2 arguments");
        }

        sqlAppender.append("(");
        sqlAstArguments.get(0).accept(walker);
        sqlAppender.append(" <% ");
        sqlAstArguments.get(1).accept(walker);
        sqlAppender.append(")");
    }
}
