package org.basex.query.func.array;

import org.basex.query.*;
import org.basex.query.value.array.Array;
import org.basex.query.value.item.*;
import org.basex.util.*;

/**
 * Function implementation.
 *
 * @author BaseX Team 2005-16, BSD License
 * @author Christian Gruen
 */
public final class ArrayInsertBefore extends ArrayFn {
  @Override
  public Item item(final QueryContext qc, final InputInfo ii) throws QueryException {
    final Array array = toArray(exprs[0], qc);
    final long p = checkPos(array, toLong(exprs[1], qc), true);
    return array.insertBefore(p, qc.value(exprs[2]));
  }
}
