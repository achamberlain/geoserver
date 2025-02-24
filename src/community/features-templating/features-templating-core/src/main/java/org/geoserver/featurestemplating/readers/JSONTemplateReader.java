/* (c) 2019 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.featurestemplating.readers;

import static org.geoserver.featurestemplating.builders.VendorOptions.FLAT_OUTPUT;
import static org.geoserver.featurestemplating.builders.VendorOptions.SEPARATOR;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.geoserver.featurestemplating.builders.AbstractTemplateBuilder;
import org.geoserver.featurestemplating.builders.SourceBuilder;
import org.geoserver.featurestemplating.builders.TemplateBuilder;
import org.geoserver.featurestemplating.builders.TemplateBuilderMaker;
import org.geoserver.featurestemplating.builders.VendorOptions;
import org.geoserver.featurestemplating.builders.impl.RootBuilder;
import org.geoserver.featurestemplating.expressions.TemplateCQLManager;
import org.geoserver.platform.FileWatcher;
import org.geotools.filter.LiteralExpressionImpl;
import org.opengis.filter.expression.Expression;

/** Produce the builder tree starting from the evaluation of json-ld template file * */
public class JSONTemplateReader implements TemplateReader {

    public static final String SOURCEKEY = "$source";

    public static final String CONTEXTKEY = "@context";

    public static final String FILTERKEY = "$filter";

    public static final String INCLUDEKEY = "$include";

    public static final String EXPRSTART = "${";

    public static final String VENDOROPTION = "$options";

    private JsonNode template;

    private TemplateReaderConfiguration configuration;

    private List<FileWatcher<Object>> watchers;

    public JSONTemplateReader(
            JsonNode template,
            TemplateReaderConfiguration configuration,
            List<FileWatcher<Object>> watchers) {
        this.template = template;
        this.configuration = configuration;
        this.watchers = watchers;
    }

    /**
     * Get a builder tree as a ${@link RootBuilder} mapping it from a Json template
     *
     * @return
     */
    @Override
    public RootBuilder getRootBuilder() {
        TemplateBuilderMaker builderMaker = configuration.getBuilderMaker();
        if (template.has(CONTEXTKEY))
            builderMaker.encodingOption(CONTEXTKEY, template.get(CONTEXTKEY));
        builderMaker.rootBuilder(true);
        RootBuilder root = (RootBuilder) builderMaker.build();
        builderMaker.namespaces(configuration.getNamespaces());
        getBuilderFromJson(null, template, root, builderMaker);
        return root;
    }

    private void getBuilderFromJson(
            String nodeName,
            JsonNode node,
            TemplateBuilder currentBuilder,
            TemplateBuilderMaker maker) {
        if (node.isObject()) {
            getBuilderFromJsonObject(node, currentBuilder, maker);
        } else if (node.isArray()) {
            getBuilderFromJsonArray(nodeName, node, currentBuilder, maker);
        } else {
            getBuilderFromJsonAttribute(nodeName, node, currentBuilder, maker);
        }
    }

    private void getBuilderFromJsonObject(
            JsonNode node, TemplateBuilder currentBuilder, TemplateBuilderMaker maker) {
        // check special node at beginning of arrays, controlling the array contents
        if (isArrayControlNode(node)) {
            if (node.has(SOURCEKEY)) {
                String source = node.get(SOURCEKEY).asText();
                ((SourceBuilder) currentBuilder).setSource(source);
            }
            if (node.has(FILTERKEY)) {
                setFilterToBuilder(currentBuilder, node);
            }
        } else {
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> nodEntry = iterator.next();
                String entryName = nodEntry.getKey();
                JsonNode valueNode = nodEntry.getValue();
                String strValueNode = valueNode.toString();
                // These fields have to be jumped cause they got writed
                // before feature evaluation starts
                boolean jumpField =
                        (entryName.equalsIgnoreCase("type")
                                        && valueNode.asText().equals("FeatureCollection"))
                                || entryName.equalsIgnoreCase("features");
                if (entryName.equals(SOURCEKEY)) {
                    String source = valueNode.asText();
                    currentBuilder = createCompositeIfNeeded(currentBuilder, maker);
                    if (currentBuilder instanceof SourceBuilder) {
                        ((SourceBuilder) currentBuilder).setSource(source);
                    }
                } else if (entryName.equals(FILTERKEY)) {
                    currentBuilder = createCompositeIfNeeded(currentBuilder, maker);
                    setFilterToBuilder(currentBuilder, node);
                } else if (entryName.equals(CONTEXTKEY)) {
                    RootBuilder rootBuilder = (RootBuilder) currentBuilder;
                    if (rootBuilder.getEncodingHints().get(CONTEXTKEY) == null) {
                        rootBuilder.getEncodingHints().put(CONTEXTKEY, valueNode);
                    }
                } else if (entryName.equals(VENDOROPTION)) {
                    setVendorOptions(valueNode, (RootBuilder) currentBuilder, maker);
                } else if (!strValueNode.contains(EXPRSTART)
                        && !strValueNode.contains(FILTERKEY)
                        && !jumpField) {
                    currentBuilder = createCompositeIfNeeded(currentBuilder, maker);
                    maker.name(entryName).jsonNode(valueNode);
                    currentBuilder.addChild(maker.build());
                } else {
                    if (valueNode.isObject()) {
                        maker.name(entryName);
                        // if the parent of the template builder being created
                        // is a root one, in case of simplified template support,
                        // or hasNotOwnOutput is flagged the CompositeBuilder being produced
                        // maps the topLevelFeature source.
                        maker.topLevelFeature(isRootOrHasNotOwnOutput(currentBuilder));
                        TemplateBuilder compositeBuilder = maker.build();
                        currentBuilder.addChild(compositeBuilder);
                        getBuilderFromJsonObject(valueNode, compositeBuilder, maker);
                    } else if (valueNode.isArray()) {
                        getBuilderFromJsonArray(entryName, valueNode, currentBuilder, maker);
                    } else {
                        if (!jumpField) {
                            currentBuilder = createCompositeIfNeeded(currentBuilder, maker);
                            getBuilderFromJsonAttribute(
                                    entryName, valueNode, currentBuilder, maker);
                        }
                    }
                }
            }
        }
    }

    private void getBuilderFromJsonArray(
            String nodeName,
            JsonNode node,
            TemplateBuilder currentBuilder,
            TemplateBuilderMaker maker) {
        TemplateBuilder iteratingBuilder =
                maker.name(nodeName)
                        .collection(true)
                        // if the parent of the template builder being created
                        // is a root one, in case of simplified template support,
                        // or hasNotOwnOutput is flagged the CompositeBuilder being produced
                        // maps the topLevelFeature source.
                        .topLevelFeature(isRootOrHasNotOwnOutput(currentBuilder))
                        .build();
        currentBuilder.addChild(iteratingBuilder);
        if (!node.toString().contains(EXPRSTART) && !node.toString().contains(FILTERKEY)) {
            maker.name(nodeName).jsonNode(node);
            currentBuilder.addChild(maker.build());
        } else {
            Iterator<JsonNode> arrayIterator = node.elements();
            while (arrayIterator.hasNext()) {
                JsonNode childNode = arrayIterator.next();
                if (childNode.isObject()) {
                    String childJSON = childNode.toString();
                    if (isArrayControlNode(childNode)) {
                        // special object controlling array contents
                        getBuilderFromJsonObject(childNode, iteratingBuilder, maker);
                    } else if (childJSON.contains(EXPRSTART) || childJSON.contains(FILTERKEY)) {
                        // regular dynamic object/filtered object
                        TemplateBuilder compositeBuilder =
                                maker.topLevelFeature(isRootOrHasNotOwnOutput(currentBuilder))
                                        .build();
                        iteratingBuilder.addChild(compositeBuilder);
                        getBuilderFromJsonObject(childNode, compositeBuilder, maker);
                    } else {
                        // static node
                        maker.jsonNode(childNode);
                        iteratingBuilder.addChild(maker.build());
                    }
                } else if (childNode.isArray()) {
                    getBuilderFromJsonArray(null, childNode, iteratingBuilder, maker);
                } else {
                    getBuilderFromJsonAttribute(null, childNode, iteratingBuilder, maker);
                }
            }
        }
    }

    private boolean isArrayControlNode(JsonNode node) {
        return (node.size() == 1 && (node.has(SOURCEKEY) || node.has(FILTERKEY)))
                || (node.size() == 2 && node.has(SOURCEKEY) && node.has(FILTERKEY));
    }

    private void getBuilderFromJsonAttribute(
            String nodeName,
            JsonNode node,
            TemplateBuilder currentBuilder,
            TemplateBuilderMaker maker) {
        String strNode = node.asText();
        if (!node.asText().contains("FeatureCollection")) {
            maker.name(nodeName).contentAndFilter(strNode);
            TemplateBuilder builder = maker.build();
            currentBuilder.addChild(builder);
        }
    }

    private void setFilterToBuilder(TemplateBuilder builder, JsonNode node) {
        String filter = node.get(FILTERKEY).asText();
        ((AbstractTemplateBuilder) builder).setFilter(filter);
    }

    private void setVendorOptions(JsonNode node, RootBuilder builder, TemplateBuilderMaker maker) {
        if (!node.isObject()) {
            throw new RuntimeException("VendorOptions should be defined as a JSON object");
        }
        if (node.has(FLAT_OUTPUT)) {
            TemplateCQLManager cqlManager =
                    new TemplateCQLManager(node.get(FLAT_OUTPUT).asText(), null);
            builder.addVendorOption(FLAT_OUTPUT, cqlManager.getExpressionFromString());
        }
        if (node.has(VendorOptions.SEPARATOR)) {
            TemplateCQLManager cqlManager =
                    new TemplateCQLManager(node.get(SEPARATOR).asText(), null);
            builder.addVendorOption(VendorOptions.SEPARATOR, cqlManager.getExpressionFromString());
        }

        if (node.has(CONTEXTKEY)) {
            builder.addVendorOption(CONTEXTKEY, node.get(CONTEXTKEY));
        }
        Expression flatOutput =
                builder.getVendorOptions()
                        .get(FLAT_OUTPUT, Expression.class, new LiteralExpressionImpl(false));
        boolean bFlatOutput = flatOutput.evaluate(null, Boolean.class).booleanValue();
        Expression expression =
                builder.getVendorOptions()
                        .get(
                                VendorOptions.SEPARATOR,
                                Expression.class,
                                new LiteralExpressionImpl("_"));
        maker.flatOutput(bFlatOutput).separator(expression.evaluate(null, String.class));
    }

    // create a composite as direct child of a Root builder
    // needed for the case where we have a template not defining the features array but only
    // the feature attributes template
    private TemplateBuilder createCompositeIfNeeded(
            TemplateBuilder currentParent, TemplateBuilderMaker maker) {
        TemplateBuilder builder;
        if (currentParent instanceof RootBuilder) {
            maker.topLevelFeature(true);
            builder = maker.build();
            currentParent.addChild(builder);
        } else builder = currentParent;
        return builder;
    }

    private boolean isRootOrHasNotOwnOutput(TemplateBuilder parent) {
        return parent instanceof RootBuilder || !((SourceBuilder) parent).hasOwnOutput();
    }
}
