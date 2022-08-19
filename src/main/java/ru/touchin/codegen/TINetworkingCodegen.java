package ru.touchin.codegen;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.helper.ConditionalHelpers;
import io.swagger.codegen.v3.*;
import io.swagger.codegen.v3.generators.handlebars.BaseItemsHelper;
import io.swagger.codegen.v3.generators.handlebars.ExtensionHelper;
import io.swagger.codegen.v3.generators.kotlin.AbstractKotlinCodegen;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

public class TINetworkingCodegen extends AbstractKotlinCodegen {

    public static final String DATE_LIBRARY = "dateLibrary";
    public static final String PROJECT_NAME = "projectName";
    private static Logger LOGGER = LoggerFactory.getLogger(TINetworkingCodegen.class);

    protected String dateLibrary = DateLibrary.JODA_TIME.value;
    protected String projectName = "SwaggerAPI";
    private Map<String, String> allCustomDateFormats = new HashMap<>();
    private Set<String> allEnumAdapters = new HashSet<>();
    private Set<String> allEnumAdaptersImports = new HashSet<>();

    public enum DateLibrary {
        STRING("string"),
        JAVA8("java8"),
        JODA_TIME("jodaTime");

        public final String value;

        DateLibrary(String value) {
            this.value = value;
        }
    }

    public TINetworkingCodegen() {
        super();
        this.typeMapping.put("array", "kotlin.collections.List");
        this.typeMapping.put("list", "kotlin.collections.List");

        artifactId = "kotlin-client";
        groupId = "ru.touchin";

        outputFolder = "generated-code" + File.separator + "kotlin-client";
        modelTemplateFiles.put("model.mustache", ".kt");
        apiTemplateFiles.put("api.mustache", ".kt");

        CliOption dateLibrary = new CliOption(DATE_LIBRARY, "Option. Date library to use");
        Map<String, String> dateOptions = new HashMap<>();
        dateOptions.put(DateLibrary.STRING.value, "String");
        dateOptions.put(DateLibrary.JAVA8.value, "Java 8 native JSR310");
        dateOptions.put(DateLibrary.JODA_TIME.value, "JodaTime");
        dateLibrary.setEnum(dateOptions);
        cliOptions.add(dateLibrary);

        cliOptions.add(new CliOption(PROJECT_NAME, "Project name in Xcode"));
    }

    @Override
    public void addHandlebarHelpers(Handlebars handlebars) {
        super.addHandlebarHelpers(handlebars);
        handlebars.registerHelpers(ConditionalHelpers.class);
    }

    @Override
    public String getDefaultTemplateDir() {
        return "TINetworking";
    }

    public CodegenType getTag() {
        return CodegenType.CLIENT;
    }

    public String getName() {
        return "TINetworking";
    }

    public String getHelp() {
        return "Generates a TINetworking kotlin client library.";
    }

    public void setDateLibrary(String library) {
        this.dateLibrary = library;
    }

    @Override
    public List<SupportingFile> supportingFiles() {
        List<SupportingFile> supportingFiles = super.supportingFiles();

        supportingFiles.add(new SupportingFile("APIDateFormat.mustache",
                sourceFolder,
                "APIDateFormat.kt"));
        supportingFiles.add(new SupportingFile("GeneratedDateAdapter.mustache",
                sourceFolder,
                "GeneratedDateAdapter.kt"));
        supportingFiles.add(new SupportingFile("EnumJsonAdapters.mustache",
                sourceFolder,
                "EnumJsonAdapters.kt"));

        return supportingFiles;
    }

    @Override
    public void processOpts() {
        setupProjectName();
        super.processOpts();

        if (additionalProperties.containsKey(DATE_LIBRARY)) {
            setDateLibrary(additionalProperties.get(DATE_LIBRARY).toString());
        }

        if (DateLibrary.JODA_TIME.value.equals(dateLibrary)) {
            additionalProperties.put(DateLibrary.JODA_TIME.value, true);
            typeMapping.put("date", "DateTime");
            typeMapping.put("date-time", "DateTime");
            typeMapping.put("time", "DateTime");
            typeMapping.put("DateTime", "DateTime");
            importMapping.put("DateTime", "org.joda.time.DateTime");
            defaultIncludes.add("org.joda.time.DateTime");
        } else if (DateLibrary.STRING.value.equals(dateLibrary)) {
            typeMapping.put("date-time", "kotlin.String");
            typeMapping.put("date", "kotlin.String");
            typeMapping.put("Date", "kotlin.String");
            typeMapping.put("DateTime", "kotlin.String");
        } else if (DateLibrary.JAVA8.value.equals(dateLibrary)) {
            additionalProperties.put(DateLibrary.JAVA8.value, true);
        }
        supportingFiles.add(new SupportingFile("manifest.mustache", "src/main", "AndroidManifest.xml"));

        supportingFiles.add(new SupportingFile("build.gradle.mustache", "", "build.gradle.kts"));
        supportingFiles.add(new SupportingFile("settings.gradle.mustache", "", "settings.gradle"));

    }

    private void setupProjectName() {
        if (additionalProperties.containsKey(PROJECT_NAME)) {
            this.projectName = (String) additionalProperties.get(PROJECT_NAME);
        } else {
            additionalProperties.put(PROJECT_NAME, projectName);
        }
        packageName = groupId + "." + projectName;

        modelPackage = packageName + "." + "models";
        apiPackage = packageName + "." + "apis";
    }

    @Override
    public Map<String, Object> postProcessSupportingFileData(Map<String, Object> objs) {
        Map<String, Object> supportingFileData = super.postProcessSupportingFileData(objs);
        supportingFileData.put("apiDateFormats", allCustomDateFormats);
        supportingFileData.put("enumAdapters", allEnumAdapters);
        supportingFileData.put("enumAdaptersImports", allEnumAdaptersImports);
        return supportingFileData;
    }

    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        super.postProcessModelProperty(model, property);

        updateEnumAdapters(model, property);

        updateVendorExtensionsForProperty(property);
        if (property.getIsListContainer()) {
            updateVendorExtensionsForProperty(property.items);
        }

        if (property.getIsObject() && isReservedWord(property.getDatatype())) {
            property.datatype = escapeReservedTypeDeclaration(property.getDatatype());
            property.datatypeWithEnum = property.datatype;
        }
    }

    private void updateEnumAdapters(CodegenModel model, CodegenProperty property) {
        boolean isEnum = ExtensionHelper.getBooleanValue(property, "x-is-enum");
        if (isEnum) {
            String enumAdapterName = model.name + "." + property.enumName + ".JsonAdapter";
            String importName = modelPackage + "." + model.name;
            allEnumAdapters.add(enumAdapterName);
            allEnumAdaptersImports.add(importName);
        }
    }

    @Override
    public String toParamName(String name) {
        return super.toParamName(name).replaceAll("[^A-Za-z0-9_]", "");
    }

    @Override
    protected void updateDataTypeWithEnumForArray(CodegenProperty property) {
        CodegenProperty baseItem = BaseItemsHelper.getBaseItemsProperty(property);
        if (baseItem != null) {
            property.datatypeWithEnum = property.datatypeWithEnum.replace(baseItem.baseType, this.toEnumName(baseItem));
            property.datatype = baseItem.baseType;
            property.enumName = this.toEnumName(property);
            if (property.defaultValue != null) {
                property.defaultValue = property.defaultValue.replace(baseItem.baseType, this.toEnumName(baseItem));
            }
        }
    }

    @Override
    public String toEnumName(CodegenProperty property) {
        return StringUtils.capitalize(property.name);
    }

    private String escapeReservedTypeDeclaration(String name) {
        return "Model" + name;
    }

    private void updateVendorExtensionsForProperty(CodegenProperty property) {
        Map<String, Object> vendorExtensions = property.getVendorExtensions();

        if (isISO8601DateProperty(property)) {
            vendorExtensions.put(TINetworkingCodegenConstants.IS_ISO8601_DATE, true);
        } else if (isCustomDateFormatProperty(property)) {
            String customDateFormat = (String) vendorExtensions.get(TINetworkingCodegenConstants.DATE_FORMAT);
            String dateFormatName = customDateFormat.replace(".", "_")
                    .replaceAll("[^A-Za-z0-9_]", "");
            vendorExtensions.put(TINetworkingCodegenConstants.DATE_FORMAT_NAME, dateFormatName);

            allCustomDateFormats.put(dateFormatName, customDateFormat);
        }
    }

    private boolean isISO8601DateProperty(CodegenProperty property) {
        return property.getIsDate()
                || property.getIsDateTime()
                && !isCustomDateFormatProperty(property);
    }

    private boolean isCustomDateFormatProperty(CodegenProperty property) {
        return property.getVendorExtensions().containsKey(TINetworkingCodegenConstants.DATE_FORMAT);
    }
}
