package org.geoserver.featurestemplating.response;

import java.io.IOException;
import org.geoserver.featurestemplating.configuration.SupportedFormat;
import org.geoserver.featurestemplating.configuration.TemplateIdentifier;
import org.junit.Test;
import org.w3c.dom.Document;

public class HTMLComplexFeaturesResponseApiTest extends TemplateComplexTestSupport {

    @Test
    public void getFilteredMappedFeature() throws IOException {
        String requestParam = "HTMLGetFilteredMappedFeature";
        String condition = "requestParam('" + requestParam + "')='true'";
        setUpTemplate(
                condition,
                SupportedFormat.HTML,
                "HTMLFilteredMappedFeature.xhtml",
                requestParam,
                ".xhtml",
                "gsml",
                mappedFeature);
        Document doc =
                getAsDOM(
                        "ogc/features/collections/gsml:MappedFeature/items?f=text/html"
                                + "&"
                                + requestParam
                                + "=true");

        assertXpathCount(1, "//html/head/script", doc);
        assertXpathCount(1, "//html/head/style", doc);
        assertXpathCount(1, "//html/head/link", doc);
        assertXpathEvaluatesTo("stylesheet", "//html/head/link/@rel", doc);
        assertXpathEvaluatesTo("some/css/href", "//html/head/link/@href", doc);
        assertXpathEvaluatesTo("text/css", "//html/head/link/@type", doc);
        assertXpathEvaluatesTo("all", "//html/head/link/@media", doc);

        assertXpathCount(5, "//html/body/ul/li[./span = 'MappedFeature']", doc);
        assertXpathCount(1, "//html/body/ul/li/ul[./li = 'mf1']", doc);
        assertXpathCount(1, "//html/body/ul/li/ul[./li = 'mf2']", doc);
        assertXpathCount(1, "//html/body/ul/li/ul[./li = 'mf3']", doc);
        assertXpathCount(1, "//html/body/ul/li/ul[./li = 'mf4']", doc);
        assertXpathCount(1, "//html/body/ul/li/ul[./li = 'mf5']", doc);

        assertXpathCount(1, "//html/body/ul/li/ul/li/ul[./li = 'GUNTHORPE FORMATION']", doc);
        assertXpathCount(1, "//html/body/ul/li/ul/li/ul[./li = 'MERCIA MUDSTONE GROUP']", doc);
        assertXpathCount(1, "//html/body/ul/li/ul/li/ul[./li = 'CLIFTON FORMATION']", doc);
        assertXpathCount(1, "//html/body/ul/li/ul/li/ul[./li = 'MURRADUC BASALT']", doc);
        assertXpathCount(1, "//html/body/ul/li/ul/li/ul[./li = 'IDONTKNOW']", doc);

        assertXpathCount(5, "//html/body/ul/li/ul/li[./span = 'Shape']", doc);

        assertXpathCount(1, "//html/body/ul/li/ul/li[./span = 'Specifications']", doc);
        assertXpathCount(1, "//html/body/ul/li/ul/li/ul/li[./span = 'Geologic Unit']", doc);
        assertXpathCount(1, "//html/body/ul/li/ul/li/ul/li/ul/li[./span = 'Purpose']", doc);
        assertXpathCount(1, "//html/body/ul/li/ul/li/ul/li/ul/li/ul[./li = 'instance']", doc);
        assertXpathCount(1, "//html/body/ul/li/ul/li/ul/li/ul/li/ul[./li = 'New Group']", doc);
        assertXpathCount(1, "//html/body/ul/li/ul/li/ul/li/ul/li/ul[./li = '-Xy']", doc);
        assertXpathCount(
                1, "//html/body/ul/li/ul/li/ul/li/ul/li[./span = 'Composition Parts']", doc);
        assertXpathCount(1, "//html/body/ul/li/ul/li/ul/li/ul/li/ul/li[./span = 'Part']", doc);
        assertXpathCount(
                1, "//html/body/ul/li/ul/li/ul/li/ul/li/ul/li/ul/li[./span = 'Role']", doc);
        assertXpathCount(
                1,
                "//html/body/ul/li/ul/li/ul/li/ul/li/ul/li/ul/li/ul[./li = 'interbedded component']",
                doc);
    }

    @Override
    protected String getTemplateFileName() {
        return TemplateIdentifier.HTML.getFilename();
    }
}
