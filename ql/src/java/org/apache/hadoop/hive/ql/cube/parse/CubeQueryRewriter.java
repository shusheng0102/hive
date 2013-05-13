package org.apache.hadoop.hive.ql.cube.parse;

import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.ql.parse.ASTNode;
import org.apache.hadoop.hive.ql.parse.ParseException;
import org.apache.hadoop.hive.ql.parse.SemanticException;

public class CubeQueryRewriter {
  private final Configuration conf;
  private final List<ContextRewriter> phase1Rewriters =
      new ArrayList<ContextRewriter>();
  private final List<ContextRewriter> phase2Rewriters =
      new ArrayList<ContextRewriter>();

  public CubeQueryRewriter(Configuration conf) {
    this.conf = conf;
    setupPhase1Rewriters();
    setupPhase2Rewriters();
  }

  private void setupPhase1Rewriters() {
    // Resolve joins and generate base join tree
    phase1Rewriters.add(new JoinResolver(conf));
    // Rewrite base trees (groupby, having, orderby, limit) using aliases
    phase1Rewriters.add(new AliasReplacer(conf));
    // Resolve aggregations and generate base select tree
    phase1Rewriters.add(new AggregateResolver(conf));
    phase1Rewriters.add(new GroupbyResolver(conf));
    // Resolve partition columns and table names
    phase1Rewriters.add(new PartitionResolver(conf));
  }

  private void setupPhase2Rewriters() {
    phase2Rewriters.add(new StorageTableResolver(conf));
    phase2Rewriters.add(new LeastPartitionResolver(conf));
    phase2Rewriters.add(new LightestFactResolver(conf));
    phase2Rewriters.add(new LeastDimensionResolver(conf));
  }

  public CubeQueryContext rewritePhase1(ASTNode astnode)
      throws SemanticException, ParseException {
    CubeSemanticAnalyzer analyzer = new CubeSemanticAnalyzer(
        new HiveConf(conf, HiveConf.class));
    analyzer.analyzeInternal(astnode);
    CubeQueryContext ctx = analyzer.getQueryContext();
    rewrite(phase1Rewriters, ctx);
    return ctx;
  }

  public CubeQueryContext rewritePhase2(CubeQueryContext cubeql,
      List<String> storages) throws SemanticException {
    cubeql.setSupportedStorages(storages);
    rewrite(phase2Rewriters, cubeql);
    return cubeql;
  }


  private void rewrite(List<ContextRewriter> rewriters, CubeQueryContext ctx)
      throws SemanticException {
    for (ContextRewriter rewriter : rewriters) {
      rewriter.rewriteContext(ctx);
    }
  }
}
