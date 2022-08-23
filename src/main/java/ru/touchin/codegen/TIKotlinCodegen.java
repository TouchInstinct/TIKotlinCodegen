package ru.touchin.codegen;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.helper.ConditionalHelpers;
import io.swagger.codegen.v3.*;
import io.swagger.codegen.v3.generators.handlebars.BaseItemsHelper;
import io.swagger.codegen.v3.generators.handlebars.ExtensionHelper;
import io.swagger.codegen.v3.generators.kotlin.AbstractKotlinCodegen;
import io.swagger.v3.oas.models.media.DateSchema;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

public class TIKotlinCodegen extends AbstractKotlinCodegen {

    public static final String DATE_LIBRARY = "dateLibrary";
    public static final String PROJECT_NAME = "projectName";
    private static Logger LOGGER = LoggerFactory.getLogger(TIKotlinCodegen.class);

    protected String dateLibrary = DateLibrary.JODA_TIME.value;
    protected String projectName = "SwaggerAPI";
    private Map<String, String> allCustomDateFormats = new HashMap<>();
    private Set<String> allJsonAdapters = new HashSet<>();
    private Set<String> allJsonAdaptersImports = new HashSet<>();

    public enum DateLibrary {
        STRING("string"),
        JAVA8("java8"),
        JODA_TIME("jodaTime");

        public final String value;

        DateLibrary(String value) {
            this.value = value;
        }
    }

    public TIKotlinCodegen() {
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
        return "TIKotlinCodegen";
    }

    public CodegenType getTag() {
        return CodegenType.CLIENT;
    }

    public String getName() {
        return "TIKotlinCodegen";
    }

    public String getHelp() {
        return "Generates a kotlin client library.";
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
        supportingFiles.add(new SupportingFile("JsonAdapters.mustache",
                sourceFolder,
                "JsonAdapters.kt"));
        supportingFiles.add(new SupportingFile("AuthorizationInterceptor.mustache",
                sourceFolder,
                "AuthorizationInterceptor.kt"));

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
        supportingFileData.put("jsonAdapters", allJsonAdapters);
        supportingFileData.put("jsonAdaptersImports", allJsonAdaptersImports);
        return supportingFileData;
    }

    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        super.postProcessModelProperty(model, property);

        if (typeAliases.containsKey(property.baseType)) {
            Schema resolvedPropertySchema = getOpenAPI().getComponents().getSchemas().get(property.datatype);

            postProcessAliasProperty(property, resolvedPropertySchema);
        }

        addEnumJsonAdapters(model, property);

        updateVendorExtensionsForProperty(property);
        if (property.getIsListContainer()) {
            updateVendorExtensionsForProperty(property.items);
        }

        if (isDateFormatProperty(property)) {
            model.getVendorExtensions().put(TIKotlinCodegenConstants.HAS_DATE, Boolean.TRUE);
            addJsonAdaptersForModelWithDate(model);
        }

        if (property.getIsObject() && isReservedWord(property.getDatatype())) {
            property.datatype = escapeReservedTypeDeclaration(property.getDatatype());
            property.datatypeWithEnum = property.datatype;
        }
    }

    private void addEnumJsonAdapters(CodegenModel model, CodegenProperty property) {
        boolean isEnum = ExtensionHelper.getBooleanValue(property, "x-is-enum");
        if (isEnum) {
            String enumAdapterName = model.name + "." + property.enumName + ".JsonAdapter";
            String importName = modelPackage + "." + model.name;
            allJsonAdapters.add(enumAdapterName);
            allJsonAdaptersImports.add(importName);
        }
    }

    private void addJsonAdaptersForModelWithDate(CodegenModel model) {
        String jsonAdapterName = model.name + "." + model.name + "JsonAdapter";
        String importName = modelPackage + "." + model.name;
        allJsonAdapters.add(jsonAdapterName);
        allJsonAdaptersImports.add(importName);

    }

    @Override
    public String toParamName(String name) {
        return replaceSpecialCharacters(super.toParamName(name));
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
            vendorExtensions.put(TIKotlinCodegenConstants.IS_ISO8601_DATE, true);
            vendorExtensions.put(TIKotlinCodegenConstants.IS_DATE_FORMAT, true);
        } else if (isCustomDateFormatProperty(property)) {
            String customDateFormat = (String) vendorExtensions.get(TIKotlinCodegenConstants.DATE_FORMAT);
            String dateFormatName = replaceSpecialCharacters(customDateFormat.replace(".", "_"));
            vendorExtensions.put(TIKotlinCodegenConstants.DATE_FORMAT_NAME, dateFormatName);
            vendorExtensions.put(TIKotlinCodegenConstants.IS_DATE_FORMAT, true);

            allCustomDateFormats.put(dateFormatName, customDateFormat);
        }
    }

    private String replaceSpecialCharacters(String text) {
        return text.replaceAll("[^A-Za-z0-9_]", "");
    }

    private boolean isISO8601DateProperty(CodegenProperty property) {
        return property.getIsDate()
                || property.getIsDateTime()
                && !isCustomDateFormatProperty(property);
    }

    private boolean isCustomDateFormatProperty(CodegenProperty property) {
        return property.getVendorExtensions().containsKey(TIKotlinCodegenConstants.DATE_FORMAT);
    }

    private boolean isDateFormatProperty(CodegenProperty property) {
        Map<String, Object> vendorExtensions = property.getVendorExtensions();
        return vendorExtensions.containsKey(TIKotlinCodegenConstants.DATE_FORMAT)
                || property.getIsDate()
                || property.getIsDateTime();
    }

    private void postProcessAliasProperty(CodegenProperty codegenProperty, Schema resolvedPropertySchema) {
        Map<String, Object> propertyExtensions = codegenProperty.getVendorExtensions();

        propertyExtensions.put(CodegenConstants.IS_ALIAS_EXT_NAME, Boolean.TRUE);

        codegenProperty.setDescription(codegenProperty.getDescription() == null
                ? resolvedPropertySchema.getDescription()
                : codegenProperty.getDescription());

        if (resolvedPropertySchema.getExtensions() != null) {
            propertyExtensions.putAll(resolvedPropertySchema.getExtensions());
        }

        if (resolvedPropertySchema instanceof DateSchema) {
            propertyExtensions.put(CodegenConstants.IS_DATE_EXT_NAME, Boolean.TRUE);
        }

        if (resolvedPropertySchema instanceof DateTimeSchema) {
            propertyExtensions.put(CodegenConstants.IS_DATE_TIME_EXT_NAME, Boolean.TRUE);
        }
    }

    @Override
    public List<CodegenSecurity> fromSecurity(Map<String, SecurityScheme> securitySchemeMap) {
        List<CodegenSecurity> securities = super.fromSecurity(securitySchemeMap);
        Iterator it = securities.iterator();
        while(it.hasNext()) {
            CodegenSecurity security = (CodegenSecurity)it.next();
            security.getVendorExtensions().put(TIKotlinCodegenConstants.SECURITY_NAME, camelize(security.name));
            security.getVendorExtensions().put(TIKotlinCodegenConstants.SECURITY_NAME_PARAM, camelize(security.name, true));
        }
        return securities;
    }
}
