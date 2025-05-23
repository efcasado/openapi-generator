/*
 * Copyright 2018 OpenAPI-Generator Contributors (https://openapi-generator.tech)
 * Copyright 2018 SmartBear Software
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openapitools.codegen.languages;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.servers.Server;
import org.apache.commons.lang3.StringUtils;
import org.openapitools.codegen.*;
import org.openapitools.codegen.meta.features.*;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.OperationMap;
import org.openapitools.codegen.model.OperationsMap;
import org.openapitools.codegen.utils.ModelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.function.Predicate;

import static org.openapitools.codegen.utils.StringUtils.underscore;

public class CppPistacheServerCodegen extends AbstractCppCodegen {
    private final Logger LOGGER = LoggerFactory.getLogger(CppPistacheServerCodegen.class);

    protected String implFolder = "impl";
    protected boolean isAddExternalLibs = true;
    protected boolean isUseStructModel = false;
    public static final String OPTIONAL_EXTERNAL_LIB = "addExternalLibs";
    public static final String OPTIONAL_EXTERNAL_LIB_DESC = "Add the Possibility to fetch and compile external Libraries needed by this Framework.";
    public static final String OPTION_USE_STRUCT_MODEL = "useStructModel";
    public static final String OPTION_USE_STRUCT_MODEL_DESC = "Use struct-based model template instead of get/set-based model template";
    public static final String HELPERS_PACKAGE_NAME = "helpersPackage";
    public static final String HELPERS_PACKAGE_NAME_DESC = "Specify the package name to be used for the helpers (e.g. org.openapitools.server.helpers).";
    protected final String PREFIX = "";
    protected String helpersPackage = "";

    /**
     * OpenApi types that shouldn't have a namespace added with getTypeDeclaration() at generation time (for nlohmann::json)
     */
    private final Set<String> openAPITypesWithoutModelNamespace = new HashSet<>();

    /**
     * int32_t (for integer)
     */
    private static final String INT32_T = "int32_t";

    /**
     * int64_t (for long)
     */
    private static final String INT64_T = "int64_t";

    /**
     * nlohmann::json (for object, AnyType)
     */
    private static final String NLOHMANN_JSON = "nlohmann::json";

    /**
     * std:string (for date, DateTime, string, file, binary, UUID, URI, ByteArray)
     */
    private static final String STD_STRING = "std::string";

    /**
     * std:map (for map)
     */
    private static final String STD_MAP = "std::map";

    /**
     * std:set (for set)
     */
    private static final String STD_SET = "std::set";

    /**
     * std:vector (for array)
     */
    private static final String STD_VECTOR = "std::vector";

    @Override
    public CodegenType getTag() {
        return CodegenType.SERVER;
    }

    @Override
    public String getName() {
        return "cpp-pistache-server";
    }

    @Override
    public String getHelp() {
        return "Generates a C++ API server (based on Pistache)";
    }

    public CppPistacheServerCodegen() {
        super();

        // TODO: cpp-pistache-server maintainer review
        modifyFeatureSet(features -> features
                .includeDocumentationFeatures(DocumentationFeature.Readme)
                .securityFeatures(EnumSet.noneOf(SecurityFeature.class))
                .excludeGlobalFeatures(
                        GlobalFeature.XMLStructureDefinitions,
                        GlobalFeature.Callbacks,
                        GlobalFeature.LinkObjects,
                        GlobalFeature.ParameterStyling,
                        GlobalFeature.MultiServer
                )
                .excludeSchemaSupportFeatures(
                        SchemaSupportFeature.Polymorphism
                )
                .excludeParameterFeatures(
                        ParameterFeature.Cookie
                )
        );

        if (StringUtils.isEmpty(modelNamePrefix)) {
            modelNamePrefix = PREFIX;
        }

        helpersPackage = "org.openapitools.server.helpers";
        apiPackage = "org.openapitools.server.api";
        modelPackage = "org.openapitools.server.model";

        apiTemplateFiles.put("api-header.mustache", ".h");
        apiTemplateFiles.put("api-source.mustache", ".cpp");
        apiTemplateFiles.put("api-impl-header.mustache", ".h");
        apiTemplateFiles.put("api-impl-source.mustache", ".cpp");

        embeddedTemplateDir = templateDir = "cpp-pistache-server";

        cliOptions.clear();
        addSwitch(OPTIONAL_EXTERNAL_LIB, OPTIONAL_EXTERNAL_LIB_DESC, this.isAddExternalLibs);
        addOption(HELPERS_PACKAGE_NAME, HELPERS_PACKAGE_NAME_DESC, this.helpersPackage);
        addOption(RESERVED_WORD_PREFIX_OPTION, RESERVED_WORD_PREFIX_DESC, this.reservedWordPrefix);
        addSwitch(OPTION_USE_STRUCT_MODEL, OPTION_USE_STRUCT_MODEL_DESC, this.isUseStructModel);
        addOption(VARIABLE_NAME_FIRST_CHARACTER_UPPERCASE_OPTION,
                VARIABLE_NAME_FIRST_CHARACTER_UPPERCASE_DESC,
                Boolean.toString(this.variableNameFirstCharacterUppercase));

        setupSupportingFiles();

        languageSpecificPrimitives = new HashSet<>(
                Arrays.asList("int", "char", "bool", "long", "float", "double", INT32_T, INT64_T));

        typeMapping = new HashMap<>();
        typeMapping.put("date", STD_STRING);
        typeMapping.put("DateTime", STD_STRING);
        typeMapping.put("string", STD_STRING);
        typeMapping.put("integer", INT32_T);
        typeMapping.put("long", INT64_T);
        typeMapping.put("boolean", "bool");
        typeMapping.put("array", STD_VECTOR);
        typeMapping.put("map", STD_MAP);
        typeMapping.put("set", STD_SET);
        typeMapping.put("file", STD_STRING);
        typeMapping.put("object", NLOHMANN_JSON);
        typeMapping.put("binary", STD_STRING);
        typeMapping.put("number", "double");
        typeMapping.put("UUID", STD_STRING);
        typeMapping.put("URI", STD_STRING);
        typeMapping.put("ByteArray", STD_STRING);
        typeMapping.put("AnyType", NLOHMANN_JSON);

        super.importMapping = new HashMap<>();
        importMapping.put(STD_VECTOR, "#include <vector>");
        importMapping.put(STD_MAP, "#include <map>");
        importMapping.put(STD_SET, "#include <set>");
        importMapping.put(STD_STRING, "#include <string>");
        importMapping.put(NLOHMANN_JSON, "#include <nlohmann/json.hpp>");

        // nlohmann:json doesn't belong to model package
        this.openAPITypesWithoutModelNamespace.add(NLOHMANN_JSON);
    }

    private void setupSupportingFiles() {
        supportingFiles.clear();
        supportingFiles.add(new SupportingFile("api-base-header.mustache", "api", "ApiBase.h"));
        supportingFiles.add(new SupportingFile("helpers-header.mustache", "model", modelNamePrefix + "Helpers.h"));
        supportingFiles.add(new SupportingFile("helpers-source.mustache", "model", modelNamePrefix + "Helpers.cpp"));
        supportingFiles.add(new SupportingFile("main-api-server.mustache", "", modelNamePrefix + "main-api-server.cpp"));
        supportingFiles.add(new SupportingFile("cmake.mustache", "", "CMakeLists.txt"));
        supportingFiles.add(new SupportingFile("README.mustache", "", "README.md"));
    }

    @Override
    public void processOpts() {
        super.processOpts();
        if (additionalProperties.containsKey(HELPERS_PACKAGE_NAME)) {
            helpersPackage = (String) additionalProperties.get(HELPERS_PACKAGE_NAME);
        }
        if (additionalProperties.containsKey("modelNamePrefix")) {
            additionalProperties().put("prefix", modelNamePrefix);
            setupSupportingFiles();
        }
        if (additionalProperties.containsKey(RESERVED_WORD_PREFIX_OPTION)) {
            reservedWordPrefix = (String) additionalProperties.get(RESERVED_WORD_PREFIX_OPTION);
        }

        additionalProperties.put("modelNamespaceDeclarations", modelPackage.split("\\."));
        additionalProperties.put("modelNamespace", modelPackage.replaceAll("\\.", "::"));
        additionalProperties.put("apiNamespaceDeclarations", apiPackage.split("\\."));
        additionalProperties.put("apiNamespace", apiPackage.replaceAll("\\.", "::"));
        additionalProperties.put("helpersNamespaceDeclarations", helpersPackage.split("\\."));
        additionalProperties.put("helpersNamespace", helpersPackage.replaceAll("\\.", "::"));
        additionalProperties.put(RESERVED_WORD_PREFIX_OPTION, reservedWordPrefix);

        if (additionalProperties.containsKey(OPTIONAL_EXTERNAL_LIB)) {
            setAddExternalLibs(convertPropertyToBooleanAndWriteBack(OPTIONAL_EXTERNAL_LIB));
        } else {
            additionalProperties.put(OPTIONAL_EXTERNAL_LIB, isAddExternalLibs);
        }

        setupModelTemplate();
    }

    private void setupModelTemplate() {
        if (additionalProperties.containsKey(OPTION_USE_STRUCT_MODEL))
            isUseStructModel = convertPropertyToBooleanAndWriteBack(OPTION_USE_STRUCT_MODEL);

        if (isUseStructModel) {
            LOGGER.info("Using struct-based model template");
            modelTemplateFiles.put("model-struct-header.mustache", ".h");
            modelTemplateFiles.put("model-struct-source.mustache", ".cpp");
        } else {
            LOGGER.info("Using get/set-based model template");
            modelTemplateFiles.put("model-header.mustache", ".h");
            modelTemplateFiles.put("model-source.mustache", ".cpp");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toModelImport(String name) {
        // Do not reattempt to add #include on an already solved #include
        if (name.startsWith("#include")) {
            return null;
        }

        if (importMapping.containsKey(name)) {
            return importMapping.get(name);
        } else {
            return "#include \"" + name + ".h\"";
        }
    }


    @Override
    public CodegenModel fromModel(String name, Schema model) {
        // Exchange import directives from core model with ours
        CodegenModel codegenModel = super.fromModel(name, model);

        Set<String> oldImports = codegenModel.imports;
        codegenModel.imports = new HashSet<>();

        for (String imp : oldImports) {
            String newImp = toModelImport(imp);

            if (newImp != null && !newImp.isEmpty()) {
                codegenModel.imports.add(newImp);
            }
        }

        if (!codegenModel.isEnum
                && codegenModel.anyOf.size() > 1
                && codegenModel.anyOf.contains(STD_STRING)
                && !codegenModel.anyOf.contains("AnyType")
                && codegenModel.interfaces.size() == 1
        ) {
            codegenModel.vendorExtensions.put("x-is-string-enum-container", true);
        }
        return codegenModel;
    }

    @Override
    public CodegenOperation fromOperation(String path, String httpMethod, Operation operation, List<Server> servers) {
        CodegenOperation op = super.fromOperation(path, httpMethod, operation, servers);

        if (operation.getResponses() != null && !operation.getResponses().isEmpty()) {
            ApiResponse apiResponse = findMethodResponse(operation.getResponses());

            if (apiResponse != null) {
                Schema response = ModelUtils.getSchemaFromResponse(openAPI, apiResponse);
                if (response != null) {
                    CodegenProperty cm = fromProperty("response", response, false);
                    op.vendorExtensions.put("x-codegen-response", cm);
                    if ("HttpContent".equals(cm.dataType)) {
                        op.vendorExtensions.put("x-codegen-response-ishttpcontent", true);
                    }
                }
            }
        }

        String pathForPistache = path.replaceAll("\\{(.*?)}", ":$1");
        op.vendorExtensions.put("x-codegen-pistache-path", pathForPistache);

        return op;
    }

    @Override
    public OperationsMap postProcessOperationsWithModels(OperationsMap objs, List<ModelMap> allModels) {
        OperationMap operations = objs.getOperations();
        String classname = operations.getClassname();
        operations.put("classnameSnakeUpperCase", underscore(classname).toUpperCase(Locale.ROOT));
        operations.put("classnameSnakeLowerCase", underscore(classname).toLowerCase(Locale.ROOT));
        List<CodegenOperation> operationList = operations.getOperation();
        for (CodegenOperation op : operationList) {
            postProcessSingleOperation(operations, op);
        }

        return objs;
    }

    private void postProcessSingleOperation(OperationMap operations, CodegenOperation op) {
        if (op.vendorExtensions == null) {
            op.vendorExtensions = new HashMap<>();
        }

        if (op.bodyParam != null) {
            if (op.bodyParam.vendorExtensions == null) {
                op.bodyParam.vendorExtensions = new HashMap<>();
            }

            boolean isStringOrDate = op.bodyParam.isString || op.bodyParam.isDate;
            op.bodyParam.vendorExtensions.put("x-codegen-pistache-is-string-or-date", isStringOrDate);
        }

        boolean consumeJson = false;
        if (op.consumes != null) {
            Predicate<Map<String, String>> isMediaTypeJson = consume -> (consume.get("mediaType") != null && consume.get("mediaType").equals("application/json"));
            consumeJson = op.consumes.stream().anyMatch(isMediaTypeJson);
        }
        op.vendorExtensions.put("x-codegen-pistache-consumes-json", consumeJson);

        op.httpMethod = op.httpMethod.substring(0, 1).toUpperCase(Locale.ROOT) + op.httpMethod.substring(1).toLowerCase(Locale.ROOT);

        boolean isParsingSupported = true;
        for (CodegenParameter param : op.allParams) {
            boolean paramSupportsParsing = (!param.isFormParam && !param.isFile && !param.isCookieParam);
            isParsingSupported = isParsingSupported && paramSupportsParsing;

            postProcessSingleParam(param);
        }
        op.vendorExtensions.put("x-codegen-pistache-is-parsing-supported", isParsingSupported);

        // Check if any one of the operations needs a model, then at API file level, at least one model has to be included.
        Predicate<String> importNotInImportMapping = hdr -> !importMapping.containsKey(hdr);
        if (op.imports.stream().anyMatch(importNotInImportMapping)) {
            operations.put("hasModelImport", true);
        }
    }

    /**
     * postProcessSingleParam - Modifies a single parameter, adjusting generated
     * data types for Header and Query parameters.
     *
     * @param param CodegenParameter to be modified.
     */
    private void postProcessSingleParam(CodegenParameter param) {
        //TODO: This changes the info about the real type but it is needed to parse the header params
        if (param.isHeaderParam) {
            param.dataType = "std::optional<Pistache::Http::Header::Raw>";
            param.baseType = "std::optional<Pistache::Http::Header::Raw>";
        } else if (param.isQueryParam) {
            String dataTypeWithNamespace = param.isPrimitiveType ? param.dataType : prefixWithNameSpaceIfNeeded(param.dataType);

            param.dataType = "std::optional<" + dataTypeWithNamespace + ">";
            param.isOptional = true;

            if (!param.isPrimitiveType) {
                param.baseType = "std::optional<" + param.baseType + ">";
            }
        }
    }

    @Override
    public String toModelFilename(String name) {
        return toModelName(name);
    }

    @Override
    public String apiFilename(String templateName, String tag) {
        String result = super.apiFilename(templateName, tag);

        if (templateName.endsWith("impl-header.mustache")) {
            result = implFilenameFromApiFilename(result, ".h");
        } else if (templateName.endsWith("impl-source.mustache")) {
            result = implFilenameFromApiFilename(result, ".cpp");
        }
        return result;
    }

    /**
     * implFilenameFromApiFilename - Inserts the string "Impl" in front of the
     * suffix and replace "api" with "impl" directory prefix.
     *
     * @param filename Filename of the api-file to be modified
     * @param suffix   Suffix of the file (usually ".cpp" or ".h")
     * @return a filename string of impl file.
     */
    private String implFilenameFromApiFilename(String filename, String suffix) {
        String result = filename.substring(0, filename.length() - suffix.length()) + "Impl" + suffix;
        result = result.replace(apiFileFolder(), implFileFolder());
        return result;
    }

    @Override
    public String toApiFilename(String name) {
        return toApiName(name);
    }

    /**
     * Optional - type declaration. This is a String which is used by the
     * templates to instantiate your types. There is typically special handling
     * for different property types
     *
     * @return a string value used as the `dataType` field for model templates,
     * `returnType` for api templates
     */
    @Override
    public String getTypeDeclaration(Schema p) {
        String openAPIType = getSchemaType(p);

        if (ModelUtils.isArraySchema(p)) {
            Schema inner = ModelUtils.getSchemaItems(p);
            return getSchemaType(p) + "<" + getTypeDeclaration(inner) + ">";
        }
        if (ModelUtils.isMapSchema(p)) {
            Schema inner = ModelUtils.getAdditionalProperties(p);
            return getSchemaType(p) + "<std::string, " + getTypeDeclaration(inner) + ">";
        } else if (ModelUtils.isByteArraySchema(p)) {
            return STD_STRING;
        }
        if (ModelUtils.isStringSchema(p)
                || ModelUtils.isDateSchema(p)
                || ModelUtils.isDateTimeSchema(p) || ModelUtils.isFileSchema(p)
                || languageSpecificPrimitives.contains(openAPIType)) {
            return toModelName(openAPIType);
        }

        return prefixWithNameSpaceIfNeeded(openAPIType);
    }

    /**
     * Prefix an open API type with a namespace or not, depending of its current type and if it is on a list to avoid it.
     *
     * @param openAPIType Open API Type.
     * @return type prefixed with the namespace or not.
     */
    private String prefixWithNameSpaceIfNeeded(String openAPIType) {
        // Some types might not support namespace
        if (this.openAPITypesWithoutModelNamespace.contains(openAPIType) || openAPIType.startsWith("std::")) {
            return openAPIType;
        } else {
            String namespace = (String) additionalProperties.get("modelNamespace");
            return namespace + "::" + openAPIType;
        }
    }

    @Override
    public String toDefaultValue(Schema p) {
        if (ModelUtils.isStringSchema(p)) {
            if (p.getDefault() != null) {
                return "\"" + p.getDefault().toString() + "\"";
            } else {
                return "\"\"";
            }
        } else if (ModelUtils.isBooleanSchema(p)) {
            if (p.getDefault() != null) {
                return p.getDefault().toString();
            } else {
                return "false";
            }
        } else if (ModelUtils.isDateSchema(p)) {
            if (p.getDefault() != null) {
                return "\"" + p.getDefault().toString() + "\"";
            } else {
                return "\"\"";
            }
        } else if (ModelUtils.isDateTimeSchema(p)) {
            if (p.getDefault() != null) {
                return "\"" + p.getDefault().toString() + "\"";
            } else {
                return "\"\"";
            }
        } else if (ModelUtils.isNumberSchema(p)) {
            if (ModelUtils.isFloatSchema(p)) { // float
                if (p.getDefault() != null) {
                    // We have to ensure that our default value has a decimal point,
                    // because in C++ the 'f' suffix is not valid on integer literals
                    // i.e. 374.0f is a valid float but 374 isn't.
                    String defaultStr = p.getDefault().toString();
                    if (defaultStr.indexOf('.') < 0) {
                        return defaultStr + ".0f";
                    } else {
                        return defaultStr + "f";
                    }
                } else {
                    return "0.0f";
                }
            } else { // double
                if (p.getDefault() != null) {
                    return p.getDefault().toString();
                } else {
                    return "0.0";
                }
            }
        } else if (ModelUtils.isIntegerSchema(p)) {
            if (ModelUtils.isLongSchema(p)) { // long
                if (p.getDefault() != null) {
                    return p.getDefault().toString() + "L";
                } else {
                    return "0L";
                }
            } else { // integer
                if (p.getDefault() != null) {
                    return p.getDefault().toString();
                } else {
                    return "0";
                }
            }
        } else if (ModelUtils.isByteArraySchema(p)) {
            if (p.getDefault() != null) {
                return "\"" + p.getDefault().toString() + "\"";
            } else {
                return "\"\"";
            }
        } else if (ModelUtils.isMapSchema(p)) {
            String inner = getSchemaType(ModelUtils.getAdditionalProperties(p));
            return "std::map<std::string, " + inner + ">()";
        } else if (ModelUtils.isArraySchema(p)) {
            String inner = getSchemaType(ModelUtils.getSchemaItems(p));
            if (!languageSpecificPrimitives.contains(inner)) {
                inner = "std::shared_ptr<" + inner + ">";
            }
            return "std::vector<" + inner + ">()";
        } else if (!StringUtils.isEmpty(p.get$ref())) {
            return "std::make_shared<" + toModelName(ModelUtils.getSimpleRef(p.get$ref())) + ">()";
        }

        return "nullptr";
    }

    /**
     * Location to write model files. You can use the modelPackage() as defined
     * when the class is instantiated
     */
    @Override
    public String modelFileFolder() {
        return (outputFolder + "/model").replace("/", File.separator);
    }

    /**
     * Location to write api files. You can use the apiPackage() as defined when
     * the class is instantiated
     */
    @Override
    public String apiFileFolder() {
        return (outputFolder + "/api").replace("/", File.separator);
    }

    private String implFileFolder() {
        return (outputFolder + "/" + implFolder).replace("/", File.separator);
    }

    /**
     * Optional - OpenAPI type conversion. This is used to map OpenAPI types in
     * a `Schema` into either language specific types via `typeMapping` or
     * into complex models if there is not a mapping.
     *
     * @return a string value of the type or complex model for this property
     */
    @Override
    public String getSchemaType(Schema p) {
        String openAPIType = super.getSchemaType(p);
        String type = null;
        if (typeMapping.containsKey(openAPIType)) {
            type = typeMapping.get(openAPIType);
            if (languageSpecificPrimitives.contains(type))
                return toModelName(type);
        } else
            type = openAPIType;
        return toModelName(type);
    }

    @Override
    public String getTypeDeclaration(String str) {
        return toModelName(str);
    }

    /**
     * Specify whether external libraries will be added during the generation
     *
     * @param value the value to be set
     */
    public void setAddExternalLibs(boolean value) {
        isAddExternalLibs = value;
    }
}
