package water.api;

import water.Iced;

/*
 * Docs REST API handler, which provides endpoint handlers for the autogeneration of
 * Markdown (and in the future perhaps HTML and PDF) documentation for REST API endpoints
 * and payload entities (aka Schemas).
 */
public class DocsHandler<I extends DocsHandler.DocsPojo, S extends DocsBase<I, S>> extends Handler<I, DocsBase<I, S>> {
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return 1; }

  /**
   * In some cases the REST API can be directly backed by a back-end Iced class.
   * In this synthetic docs we don't have a domain class that would serve
   * that purpose, so we define one here.
   */
  protected static final class DocsPojo extends Iced {
    // Inputs
    String http_method; // GET, etc.
    int num;
    String path;
    String classname;

    // Outputs
    Route[] routes;
    SchemaMetadata[] schemas;
  }


  @SuppressWarnings("unused") // called through reflection by RequestServer
  public DocsBase listRoutes(int version, DocsPojo docsPojo) {
    docsPojo.routes = new Route[RequestServer.numRoutes()];
    int i = 0;
    for (Route route : RequestServer.routes()) {
      docsPojo.routes[i++] = route;
    }
    return schema(version).fillFromImpl(docsPojo);
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public DocsBase fetchRoute(int version, DocsPojo docsPojo) {
    Route route = null;
    if (null != docsPojo.path && null != docsPojo.http_method) {
      route = RequestServer.lookup(docsPojo.http_method, docsPojo.path);
    } else {
      int i = 0;
      for (Route r : RequestServer.routes()) {
        if (i++ == docsPojo.num) {
          route = r;
          break;
        }
      }

      docsPojo.routes = new Route[null == route ? 0 : 1];
      if (null != route) {
        docsPojo.routes[0] = route;
      }
    }
    DocsBase result = schema(version).fillFromImpl(docsPojo);
    result.routes[0].markdown = route.markdown(null).toString();
    return result;
  }


  @SuppressWarnings("unused") // called through reflection by RequestServer
  public DocsBase fetchSchemaMetadataByClass(int version, DocsPojo docsPojo) {
    DocsBase result = schema(version).fillFromImpl(docsPojo);
    result.schemas = new SchemaMetadataBase[1];
    // NOTE: this will throw IllegalArgumentException if the classname isn't found:
    SchemaMetadataBase meta = new SchemaMetadataV1().fillFromImpl(SchemaMetadata.createSchemaMetadata(docsPojo.classname));
    result.schemas[0] = meta;
    return result;
  }

  @Override protected DocsBase schema(int version) {
    if (version == 1)
      return new DocsV1();
    else
      throw new IllegalArgumentException("Bad version for Docs schema: " + version);
  } // schema()

}
