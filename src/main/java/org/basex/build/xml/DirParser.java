package org.basex.build.xml;

import static org.basex.core.Text.*;
import java.io.IOException;
import org.basex.build.Builder;
import org.basex.build.Parser;
import org.basex.core.Prop;
import org.basex.io.IO;
import org.basex.util.Util;

/**
 * This class parses the tokens that are delivered by the
 * {@link XMLScanner} and sends them to the specified database builder.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-10, ISC License
 * @author Christian Gruen
 */
public final class DirParser extends Parser {
  /** Properties. */
  private final Prop prop;
  /** File filter. */
  private final String filter;
  /** Initial file path. */
  private final String root;

  /** Parser reference. */
  private Parser parser;
  /** Element counter. */
  private int c;

  /**
   * Constructor.
   * @param f file reference
   * @param pr database properties
   */
  public DirParser(final IO f, final Prop pr) {
    this(f, pr, "");
  }

  /**
   * Constructor, specifying a target path.
   * @param path file reference
   * @param pr database properties
   * @param t target path
   */
  public DirParser(final IO path, final Prop pr, final String t) {
    super(path, t);
    prop = pr;
    final String dir = path.dir();
    root = dir.endsWith("/") ? dir : dir + '/';

    if(path.isDir()) {
      final StringBuilder sb = new StringBuilder();
      for(final String s : pr.get(Prop.CREATEFILTER).
          replaceAll("\\.", "\\\\.").replaceAll("\\*", ".*").split(",")) {
        sb.append("|" + (s.contains(".") ? s : ".*"));
      }
      filter = sb.toString().substring(1);
    } else {
      filter = ".*";
    }
  }

  @Override
  public void parse(final Builder b) throws IOException {
    b.meta.filesize = 0;
    b.meta.file = IO.get(file.path());
    parse(b, file);
  }

  /**
   * Parses the specified file or its children.
   * @param b builder
   * @param io input
   * @throws IOException I/O exception
   */
  private void parse(final Builder b, final IO io) throws IOException {
    if(io.isDir()) {
      for(final IO f : io.children()) parse(b, f);
    } else {
      file = io;
      while(io.more()) {
        if(!io.name().matches(filter)) continue;
        b.meta.filesize += file.length();

        // use global target as prefix
        String targ = target.length() != 0 ? target + '/' : "";
        final String name = file.name();
        String path = file.path();
        // add relative path without root (prefix) and file name (suffix)
        if(path.endsWith('/' + name)) {
          path = path.substring(0, path.length() - name.length());
          if(path.startsWith(root)) path = path.substring(root.length());
          targ = (targ + path).replace("//", "/");
        }
        parser = Parser.fileParser(file, prop, targ);
        parser.parse(b);

        if(Util.debug && ++c % 1000 == 0) Util.err(";");
      }
    }
  }

  @Override
  public String tit() {
    return parser != null ? parser.tit() : PROGCREATE;
  }

  @Override
  public String det() {
    return parser != null ? parser.det() : "";
  }

  @Override
  public double prog() {
    return parser != null ? parser.prog() : 0;
  }
}
