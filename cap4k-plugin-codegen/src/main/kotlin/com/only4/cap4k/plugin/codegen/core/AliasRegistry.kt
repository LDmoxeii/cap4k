package com.only4.cap4k.plugin.codegen.core

/**
 * Centralized alias registry for template variables.
 *
 * Allows decoupling alias resolution from Gradle task implementations.
 */
class AliasRegistry(
    private val rules: List<AliasRule> = DEFAULT_RULES,
) {

    data class AliasRule(
        val regex: Regex,
        val aliases: List<String>,
    ) {
        fun matches(key: String): Boolean = regex.matches(key)
    }

    /**
     * Resolve alias list by tag + variable name.
     */
    fun resolve(tag: String, variable: String): List<String> {
        val key = "$tag.$variable"
        val matched = rules.firstOrNull { it.matches(key) }
        return matched?.aliases ?: listOf(variable)
    }

    companion object {
        private val DEFAULT_RULES = listOf(
            // Module path
            AliasRule(
                regex = Regex(".+\\.modulePath"),
                aliases = listOf(
                    "modulePath",
                    "ModulePath",
                    "MODULE_PATH",
                    "module_path",
                    "Module_Path",
                    "module",
                    "Module",
                    "MODULE",
                ),
            ),
            // Template package
            AliasRule(
                regex = Regex(".+\\.templatePackage"),
                aliases = listOf(
                    "templatePackage",
                    "TemplatePackage",
                    "TEMPLATE_PACKAGE",
                    "template_package",
                    "Template_Package",
                ),
            ),
            // Imports
            AliasRule(
                regex = Regex(".+\\.Imports"),
                aliases = listOf(
                    "Imports",
                    "imports",
                    "IMPORTS",
                    "importList",
                    "ImportList",
                    "IMPORT_LIST",
                    "import_list",
                    "Import_List",
                ),
            ),
            // Comment
            AliasRule(
                regex = Regex(".+\\.Comment"),
                aliases = listOf("Comment", "comment", "COMMENT"),
            ),
            // Comment escaped
            AliasRule(
                regex = Regex(".+\\.CommentEscaped"),
                aliases = listOf(
                    "CommentEscaped",
                    "commentEscaped",
                    "COMMENT_ESCAPED",
                    "Comment_Escaped",
                ),
            ),
            // Aggregate
            AliasRule(
                regex = Regex("(schema|enum|domain_event|domain_event_handler|specification|factory)\\.Aggregate"),
                aliases = listOf("Aggregate", "aggregate", "AGGREGATE"),
            ),
            // Entity
            AliasRule(
                regex = Regex("(schema|enum|domain_event|domain_event_handler|specification|factory)\\.Entity"),
                aliases = listOf(
                    "Entity",
                    "entity",
                    "ENTITY",
                    "entityType",
                    "EntityType",
                    "ENTITY_TYPE",
                    "Entity_Type",
                    "entity_type",
                ),
            ),
            // EntityVar
            AliasRule(
                regex = Regex("(schema|enum|domain_event|domain_event_handler|specification|factory)\\.EntityVar"),
                aliases = listOf("EntityVar", "entityVar", "ENTITY_VAR", "entity_var", "Entity_Var"),
            ),
            // Schema base
            AliasRule(
                regex = Regex("(schema_base|schema)\\.SchemaBase"),
                aliases = listOf("SchemaBase", "schema_base", "SCHEMA_BASE"),
            ),
            // Schema Id
            AliasRule(
                regex = Regex("schema\\.IdField"),
                aliases = listOf("IdField", "idField", "ID_FIELD", "id_field", "Id_Field"),
            ),
            // Schema field items
            AliasRule(
                regex = Regex("schema\\.FIELD_ITEMS"),
                aliases = listOf("FIELD_ITEMS", "fieldItems", "field_items", "Field_Items"),
            ),
            // Schema join items
            AliasRule(
                regex = Regex("schema\\.JOIN_ITEMS"),
                aliases = listOf("JOIN_ITEMS", "joinItems", "join_items", "Join_Items"),
            ),
            // Schema field type
            AliasRule(
                regex = Regex("schema_field\\.fieldType"),
                aliases = listOf("fieldType", "FIELD_TYPE", "field_type", "Field_Type"),
            ),
            // Schema field name
            AliasRule(
                regex = Regex("schema_field\\.fieldName"),
                aliases = listOf("fieldName", "FIELD_NAME", "field_name", "Field_Name"),
            ),
            // Schema field comment
            AliasRule(
                regex = Regex("schema_field\\.fieldComment"),
                aliases = listOf("fieldComment", "FIELD_COMMENT", "field_comment", "Field_Comment"),
            ),
            // Enum
            AliasRule(
                regex = Regex("enum\\.Enum"),
                aliases = listOf(
                    "Enum",
                    "enum",
                    "ENUM",
                    "EnumType",
                    "enumType",
                    "ENUM_TYPE",
                    "enum_type",
                    "Enum_Type",
                ),
            ),
            // Enum value field
            AliasRule(
                regex = Regex("enum\\.EnumValueField"),
                aliases = listOf(
                    "EnumValueField",
                    "enumValueField",
                    "ENUM_VALUE_FIELD",
                    "enum_value_field",
                    "Enum_Value_Field",
                ),
            ),
            // Enum name field
            AliasRule(
                regex = Regex("enum\\.EnumNameField"),
                aliases = listOf(
                    "EnumNameField",
                    "enumNameField",
                    "ENUM_NAME_FIELD",
                    "enum_name_field",
                    "Enum_Name_Field",
                ),
            ),
            // Enum items
            AliasRule(
                regex = Regex("enum\\.EnumItems"),
                aliases = listOf("EnumItems", "ENUM_ITEMS", "enumItems", "enum_items", "Enum_Items"),
            ),
            // Domain event
            AliasRule(
                regex = Regex("(domain_event|domain_event_handler)\\.DomainEvent"),
                aliases = listOf(
                    "DomainEvent",
                    "domainEvent",
                    "DOMAIN_EVENT",
                    "domain_event",
                    "Domain_Event",
                    "Event",
                    "EVENT",
                    "event",
                    "DE",
                    "D_E",
                    "de",
                    "d_e",
                ),
            ),
            // Domain event persist
            AliasRule(
                regex = Regex("(domain_event|domain_event_handler)\\.persist"),
                aliases = listOf("persist", "Persist", "PERSIST"),
            ),
            // Domain service
            AliasRule(
                regex = Regex("domain_service\\.DomainService"),
                aliases = listOf(
                    "DomainService",
                    "domainService",
                    "DOMAIN_SERVICE",
                    "domain_service",
                    "Domain_Service",
                    "Service",
                    "SERVICE",
                    "service",
                    "Svc",
                    "SVC",
                    "svc",
                    "DS",
                    "D_S",
                    "ds",
                    "d_s",
                ),
            ),
            // Specification
            AliasRule(
                regex = Regex("specification\\.Specification"),
                aliases = listOf("Specification", "specification", "SPECIFICATION", "Spec", "SPEC", "spec"),
            ),
            // Factory
            AliasRule(
                regex = Regex("factory\\.Factory"),
                aliases = listOf("Factory", "factory", "FACTORY", "Fac", "FAC", "fac"),
            ),
            // Integration event
            AliasRule(
                regex = Regex("(integration_event|integration_event_handler)\\.IntegrationEvent"),
                aliases = listOf(
                    "IntegrationEvent",
                    "integrationEvent",
                    "integration_event",
                    "INTEGRATION_EVENT",
                    "Integration_Event",
                    "Event",
                    "EVENT",
                    "event",
                    "IE",
                    "I_E",
                    "ie",
                    "i_e",
                ),
            ),
            // Aggregate root
            AliasRule(
                regex = Regex("(specification|factory|domain_event|domain_event_handler)\\.AggregateRoot"),
                aliases = listOf(
                    "AggregateRoot",
                    "aggregateRoot",
                    "aggregate_root",
                    "AGGREGATE_ROOT",
                    "Aggregate_Root",
                    "Root",
                    "ROOT",
                    "root",
                    "AR",
                    "A_R",
                    "ar",
                    "a_r",
                ),
            ),
            // Client
            AliasRule(
                regex = Regex("(client|client_handler)\\.Client"),
                aliases = listOf("Client", "client", "CLIENT", "Cli", "CLI", "cli"),
            ),
            // Query
            AliasRule(
                regex = Regex("(query|query_handler)\\.Query"),
                aliases = listOf("Query", "query", "QUERY", "Qry", "QRY", "qry"),
            ),
            // Command
            AliasRule(
                regex = Regex("(command|command_handler)\\.Command"),
                aliases = listOf("Command", "command", "COMMAND", "Cmd", "CMD", "cmd"),
            ),
            // Request
            AliasRule(
                regex = Regex("(client|client_handler|query|query_handler|command|command_handler)\\.Request"),
                aliases = listOf(
                    "Request",
                    "request",
                    "REQUEST",
                    "Req",
                    "REQ",
                    "req",
                    "Param",
                    "PARAM",
                    "param",
                ),
            ),
            // Response
            AliasRule(
                regex = Regex("(client|client_handler|query|query_handler|command|command_handler|saga|saga_handler)\\.Response"),
                aliases = listOf(
                    "Response",
                    "response",
                    "RESPONSE",
                    "Res",
                    "RES",
                    "res",
                    "ReturnType",
                    "returnType",
                    "RETURN_TYPE",
                    "return_type",
                    "Return_Type",
                    "Return",
                    "RETURN",
                    "return",
                ),
            ),
        )
    }
}
