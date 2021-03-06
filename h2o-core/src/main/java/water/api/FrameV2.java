package water.api;

import water.*;
import water.fvec.*;
import water.parser.ValueString;
import water.util.DocGen.HTML;
import water.util.PrettyPrint;

// TODO: need a base (versionless) class!
class FrameV2 extends Schema<Frame, FrameV2> {

  // Input fields
  @API(help="Key to inspect",required=true)
  Key key;

  @API(help="Row offset to display",direction=API.Direction.INPUT)
  long off;

  @API(help="Number of rows to display",direction=API.Direction.INOUT)
  int len;

  // Output fields
  @API(help="checksum", direction=API.Direction.OUTPUT)
  protected long checksum;

  @API(help="Number of rows", direction=API.Direction.OUTPUT)
  long rows;

  @API(help="Total data size in bytes", direction=API.Direction.OUTPUT)
  long byteSize;

  @API(help="Raw unparsed text", direction=API.Direction.OUTPUT)
  boolean isText;

  @API(help="Default percentiles, from 0 to 1", direction=API.Direction.OUTPUT)
  double[] default_pctiles;

  @API(help="Columns", direction=API.Direction.OUTPUT)
  Col[] columns;

  @API(help="Compatible models, if requested", direction=API.Direction.OUTPUT)
  String[] compatible_models;

  // Output fields one-per-column
  protected static class Col extends Iced {
    @API(help="label", direction=API.Direction.OUTPUT)
    String label;

    @API(help="missing", direction=API.Direction.OUTPUT)
    long missing;

    @API(help="zeros", direction=API.Direction.OUTPUT)
    long zeros;

    @API(help="positive infinities", direction=API.Direction.OUTPUT)
    long pinfs;

    @API(help="negative infinities", direction=API.Direction.OUTPUT)
    long ninfs;

    @API(help="mins", direction=API.Direction.OUTPUT)
    double[] mins;

    @API(help="maxs", direction=API.Direction.OUTPUT)
    double[] maxs;

    @API(help="mean", direction=API.Direction.OUTPUT)
    double mean;

    @API(help="sigma", direction=API.Direction.OUTPUT)
    double sigma;

    @API(help="datatype: {enum, string, int, real, time, uuid}", direction=API.Direction.OUTPUT)
    String type;

    @API(help="domain; not-null for enum columns only", direction=API.Direction.OUTPUT)
    String[] domain;

    @API(help="data", direction=API.Direction.OUTPUT)
    double[] data;

    @API(help="string data", direction=API.Direction.OUTPUT)
    String[] str_data;

    @API(help="decimal precision, -1 for all digits", direction=API.Direction.OUTPUT)
    byte precision;

    @API(help="Histogram bins; null if not computed", direction=API.Direction.OUTPUT)
    long[] bins;

    @API(help="Start of histogram bin zero", direction=API.Direction.OUTPUT)
    double base;

    @API(help="Stride per bin", direction=API.Direction.OUTPUT)
    double stride;

    @API(help="Percentile values, matching the default percentiles", direction=API.Direction.OUTPUT)
    double[] pctiles;

    transient Vec _vec;

    Col( String name, Vec vec, long off, int len ) {
      label=name;
      missing = vec.naCnt();
      zeros = vec.length()-vec.nzCnt();
      pinfs = vec.pinfs();
      ninfs = vec.ninfs();
      mins  = vec.mins();
      maxs  = vec.maxs();
      mean  = vec.mean();
      sigma = vec.sigma();
      type  = vec.isEnum() ? "enum" : vec.isUUID() ? "uuid" : vec.isString() ? "string" : (vec.isInt() ? (vec.isTime() ? "time" : "int") : "real");
      domain = vec.domain();
      len = (int)Math.min(len,vec.length()-off);
      if( vec.isUUID() ) {
        str_data = new String[len];
        for (int i = 0; i < len; i++)
          str_data[i] = vec.isNA(off + i) ? null : PrettyPrint.UUID(vec.at16l(off + i), vec.at16h(off + i));
        data = null;
      } else if ( vec.isString() ) {
        str_data = new String[len];
        ValueString vstr = new ValueString();
        for (int i = 0; i < len; i++)
          str_data[i] = vec.isNA(off + i) ? null : vec.atStr(vstr,off + i).toString();
        data = null;
      } else {
        data = MemoryManager.malloc8d(len);
        for( int i=0; i<len; i++ )
          data[i] = vec.at(off+i);
        str_data = null;
      }
      _vec = vec;               // Better HTML display, not in the JSON
      precision = vec.chunkForRow(0).precision();

      // Histogram data is only computed on-demand.  By default here we do NOT
      // compute it, but will return any prior computed & cached histogram.
      bins  = vec.lazy_bins();
      base  = bins==null ? 0 : vec.base();
      stride= bins==null ? 0 : vec.stride();
      pctiles=bins==null ? null : vec.pctiles();
    }

    public void clearBinsField() {
      this.bins = null;
    }
  }

  // Constructor for when called from the Inspect handler instead of RequestServer
  transient Frame _fr;         // Avoid an racey update to Key; cached loaded value

  public FrameV2() { super(); }

  /* Key-only constructor, for the times we only want to return the key. */
  FrameV2( Key key ) { this.key = key; }

  FrameV2( Frame fr ) {
    this(fr, 1, (int)fr.vec(0).length()); // NOTE: possible len truncation
  }

  /** TODO: refactor together with fillFromImpl(). */
  FrameV2( Frame fr, long off2, int len2 ) {
    if( off2==0 ) off2=1;       // 1-based row-numbering; so default offset is 1
    if( len2==0 ) len2=100;     // Default length if zero passed
    key = fr._key;
    _fr = fr;
    off = off2-1;
    rows = fr.numRows();
    len = (int)Math.min(len2,rows);
    byteSize = fr.byteSize();
    columns = new Col[fr.numCols()];
    Vec[] vecs = fr.vecs();
    for( int i=0; i<columns.length; i++ )
      columns[i] = new Col(fr._names[i],vecs[i],off,len);
    isText = fr.numCols()==1 && vecs[0] instanceof ByteVec;
    default_pctiles = Vec.PERCENTILES;
    this.checksum = fr.checksum();
  }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling from the impl
  @Override public FrameV2 fillFromImpl(Frame f) {
    this.checksum = f.checksum();
    off = 0;
    rows = _fr.numRows();
    // TODO: pass in offset and column from Inspect page
    // if( h instanceof InspectHandler ) { off = ((InspectHandler)h)._off;  len = ((InspectHandler)h)._len; }
    if( off == 0 ) off = 1;     // 1-based row-numbering from REST, so default offset is 1
    if( len == 0 ) len = 100;
    off = off-1;                // 0-based row-numbering
    len = (int)Math.min(len,rows);
    byteSize = _fr.byteSize();
    columns = new Col[_fr.numCols()];
    Vec[] vecs = _fr.vecs();
    for( int i=0; i<columns.length; i++ )
      columns[i] = new Col(_fr._names[i],vecs[i],off,len);
    return this;
  }

  public void clearBinsField() {
    for (Col col: columns)
      col.clearBinsField();
  }

  @Override public HTML writeHTML_impl( HTML ab ) {
    String[] urls = RequestServer.frameChoices(getVersion(),_fr);
    for( String url : urls )
      ab.href("hex",url,url);

    // Main data display
    // Column names
    String titles[] = new String[_fr._names.length+1];
    titles[0]="";
    System.arraycopy(_fr._names,0,titles,1,_fr._names.length);
    ab.arrayHead(titles);

    // Rollup data
    final long nrows = _fr.numRows();
    formatRow(ab,"","type" ,new ColOp() { String op(Col c) { return c.type; } } );
    formatRow(ab,"","min"  ,new ColOp() { String op(Col c) { return rollUpStr(c, c.missing==nrows ? Double.NaN : c.mins[0]); } } );
    formatRow(ab,"","max"  ,new ColOp() { String op(Col c) { return rollUpStr(c, c.missing==nrows ? Double.NaN : c.maxs[0]); } } );
    formatRow(ab,"","mean" ,new ColOp() { String op(Col c) { return rollUpStr(c, c.missing==nrows ? Double.NaN : c.mean   ); } } );
    formatRow(ab,"","sigma",new ColOp() { String op(Col c) { return rollUpStr(c, c.missing==nrows ? Double.NaN : c.sigma  ); } } );

    // Optional rows: missing elements, zeros, positive & negative infinities, levels
    for( Col c : columns ) if( c.missing > 0 )
        { formatRow(ab,"class='warning'","missing",new ColOp() { String op(Col c) { return c.missing== 0 ?"":Long.toString(c.missing      );}}); break; }
    for( Col c : columns ) if( c.zeros   > 0 )
        { formatRow(ab,"class='warning'","zeros"  ,new ColOp() { String op(Col c) { return c.zeros  == 0 ?"":Long.toString(c.zeros        );}}); break; }
    for( Col c : columns ) if( c.pinfs   > 0 )
        { formatRow(ab,"class='warning'","+infins",new ColOp() { String op(Col c) { return c.pinfs  == 0 ?"":Long.toString(c.pinfs        );}}); break; }
    for( Col c : columns ) if( c.ninfs   > 0 )
        { formatRow(ab,"class='warning'","-infins",new ColOp() { String op(Col c) { return c.ninfs  == 0 ?"":Long.toString(c.ninfs        );}}); break; }
    for( Col c : columns ) if( c.domain!=null)
        { formatRow(ab,"class='warning'","levels" ,new ColOp() { String op(Col c) { return c.domain==null?"":Long.toString(c.domain.length);}}); break; }

    // Frame data
    final int len = columns.length > 0 ? columns[0].data.length : 0;
    for( int i=0; i<len; i++ ) {
      final int row = i;
      formatRow(ab,"",Long.toString(off+row+1),new ColOp() {
          String op(Col c) {
            return formatCell(c.data==null?0:c.data[row],c.str_data==null?null:c.str_data[row],c,0); }
        } );
    }

    ab.arrayTail();

    return ab.bodyTail();
  }

  private abstract static class ColOp { abstract String op(Col v); }
  private String rollUpStr(Col c, double d) {
    return formatCell(c.domain!=null || "uuid".equals(c.type) || "string".equals(c.type) ? Double.NaN : d,null,c,4);
  }

  private void formatRow( HTML ab, String color, String msg, ColOp vop ) {
    ab.p("<tr").p(color).p(">");
    ab.cell(msg);
    for( Col c : columns )  ab.cell(vop.op(c));
    ab.p("</tr>");
  }

  private String formatCell( double d, String str, Col c, int precision ) {
    if( Double.isNaN(d) ) return "-";
    if( c.domain!=null ) return c.domain[(int)d];
    if( "uuid".equals(c.type) || "string".equals(c.type)) {
      // UUID and String handling
      if( str==null ) return "-";
      return "<b style=\"font-family:monospace;\">"+str+"</b>";
    }

    long l = (long)d;
    if( (double)l == d ) return Long.toString(l);
    if( precision > 0 ) return x2(d,PrettyPrint.pow10(-precision));
    Chunk chk = c._vec.chunkForRow(off);
    Class Cc = chk.getClass();
    if( Cc == C1SChunk.class ) return x2(d,((C1SChunk)chk).scale());
    if( Cc == C2SChunk.class ) return x2(d,((C2SChunk)chk).scale());
    if( Cc == C4SChunk.class ) return x2(d,((C4SChunk)chk).scale());
    return Double.toString(d);
  }

  private static String x2( double d, double scale ) {
    String s = Double.toString(d);
    // Double math roundoff error means sometimes we get very long trailing
    // strings of junk 0's with 1 digit at the end... when we *know* the data
    // has only "scale" digits.  Chop back to actual digits
    int ex = (int)Math.log10(scale);
    int x = s.indexOf('.');
    int y = x+1+(-ex);
    if( x != -1 && y < s.length() ) s = s.substring(0,x+1+(-ex));
    while( s.charAt(s.length()-1)=='0' )
      s = s.substring(0,s.length()-1);
    return s;
  }
}
