package org.basex.query;

import java.util.*;

import org.basex.core.locks.*;
import org.basex.query.expr.*;
import org.basex.query.func.*;
import org.basex.query.iter.*;
import org.basex.query.util.*;
import org.basex.query.util.list.*;
import org.basex.query.value.item.*;
import org.basex.query.value.node.*;
import org.basex.query.value.type.*;
import org.basex.query.var.*;
import org.basex.util.*;
import org.basex.util.hash.*;
import org.basex.util.list.*;

/**
 * An XQuery main module.
 *
 * @author BaseX Team 2005-16, BSD License
 * @author Leo Woerteler
 */
public final class MainModule extends Module {
  /** Declared type, {@code null} if not specified. */
  private final SeqType declType;

  /**
   * Creates a new main module for a context item declared in the prolog.
   * @param expr root expression
   * @param scope variable scope
   * @param doc documentation
   * @param type optional type
   * @param sc static context
   * @param info input info
   * @return main module
   */
  public static MainModule get(final Expr expr, final VarScope scope, final SeqType type,
      final String doc, final StaticContext sc, final InputInfo info) {
    return new MainModule(expr, scope, type, doc, null, null, null, sc, info);
  }

  /**
   * Creates a new main module for the specified function.
   * @param uf user-defined function
   * @param args arguments
   * @return main module
   * @throws QueryException query exception
   */
  public static MainModule get(final StaticFunc uf, final Expr[] args) throws QueryException {
    final StaticFuncCall sfc = new StaticFuncCall(uf.name, args, uf.sc, uf.info).init(uf);
    return new MainModule(sfc, new VarScope(uf.sc), null, null, null, null, null, uf.sc, null);
  }

  /**
   * Constructor.
   * @param expr root expression
   * @param scope variable scope
   * @param doc documentation
   * @param funcs user-defined functions
   * @param vars static variables
   * @param imports namespace URIs of imported modules
   * @param declType optional type
   * @param sc static context
   * @param info input info
   */
  MainModule(final Expr expr, final VarScope scope, final SeqType declType, final String doc,
      final TokenObjMap<StaticFunc> funcs, final TokenObjMap<StaticVar> vars,
      final TokenSet imports, final StaticContext sc, final InputInfo info) {

    super(scope, doc, funcs, vars, imports, sc, info);
    this.expr = expr;
    this.declType = declType;
  }

  @Override
  public void compile(final QueryContext qc) throws QueryException {
    if(compiled) return;
    try {
      compiled = true;
      expr = expr.compile(qc, scope);
    } finally {
      scope.cleanUp(this);
    }
  }

  /**
   * Evaluates this module and returns the result as a cached value iterator.
   * @param qc query context
   * @return result
   * @throws QueryException evaluation exception
   */
  ItemList cache(final QueryContext qc) throws QueryException {
    final int fp = scope.enter(qc);
    try {
      final Iter iter = expr.iter(qc);
      final ItemList cache = new ItemList(Math.max(1, (int) iter.size()));
      for(Item it; (it = iter.next()) != null;) cache.add(it);
      if(declType != null) declType.treat(cache.value(), null, info);
      return cache;

    } finally {
      VarScope.exit(fp, qc);
    }
  }

  /**
   * Creates a result iterator which lazily evaluates this module.
   * @param qc query context
   * @return result iterator
   * @throws QueryException query exception
   */
  Iter iter(final QueryContext qc) throws QueryException {
    if(declType != null) return cache(qc).iter();

    final int fp = scope.enter(qc);
    final Iter iter = expr.iter(qc);
    return new Iter() {
      @Override
      public Item next() throws QueryException {
        final Item it = iter.next();
        if(it == null) VarScope.exit(fp, qc);
        return it;
      }

      @Override
      public long size() {
        return iter.size();
      }

      @Override
      public Item get(final long i) throws QueryException {
        return iter.get(i);
      }
    };
  }

  @Override
  public String toString() {
    return expr.toString();
  }

  @Override
  public void plan(final FElem e) {
    expr.plan(e);
  }

  @Override
  public boolean visit(final ASTVisitor visitor) {
    return expr.accept(visitor);
  }

  /**
   * Adds the names of the databases that may be touched by the module.
   * @param lr lock result
   * @param qc query context
   * @return result of check
   */
  boolean databases(final LockResult lr, final QueryContext qc) {
    return expr.accept(new LockVisitor(lr, qc));
  }

  /**
   * Lock visitor.
   * @author Leo Woerteler
   */
  private static final class LockVisitor extends ASTVisitor {
    /** Already visited scopes. */
    private final IdentityHashMap<Scope, Object> funcs = new IdentityHashMap<>();
    /** Reference to process list of locked databases. */
    private final StringList sl;
    /** Focus level. */
    private int level;

    /**
     * Constructor.
     * @param lr lock result
     * @param qc query context
     */
    private LockVisitor(final LockResult lr, final QueryContext qc) {
      sl = qc.updating ? lr.write : lr.read;
      level = qc.ctxItem == null ? 0 : 1;
    }

    @Override
    public boolean lock(final String db) {
      // name is unknown at compile time: return false
      if(db == null) return false;
      // if context item is found on top level, it will refer to currently opened database
      if(level == 0 || db != DBLocking.CONTEXT) sl.add(db);
      return true;
    }

    @Override
    public void enterFocus() {
      level++;
    }

    @Override
    public void exitFocus() {
      level--;
    }

    @Override
    public boolean staticVar(final StaticVar var) {
      if(funcs.containsKey(var)) return true;
      funcs.put(var, null);
      return var.visit(this);
    }

    @Override
    public boolean staticFuncCall(final StaticFuncCall call) {
      return func(call.func());
    }

    @Override
    public boolean inlineFunc(final Scope sub) {
      enterFocus();
      final boolean ac = sub.visit(this);
      exitFocus();
      return ac;
    }

    @Override
    public boolean funcItem(final FuncItem func) {
      return func(func);
    }

    /**
     * Visits a scope.
     * @param scp scope
     * @return if more expressions should be visited
     */
    private boolean func(final Scope scp) {
      if(funcs.containsKey(scp)) return true;
      funcs.put(scp, null);
      enterFocus();
      final boolean ac = scp.visit(this);
      exitFocus();
      return ac;
    }
  }
}
