define(["jsonld"], function (jsonld) {
    "use strict";

    const DESC = {
        "$namespace": "http://plugins.linkedpipes.com/ontology/t-tabularUv#",
        "$type": "Configuration",
        "$options": {
            "$predicate": "auto",
            "$control": "auto"
        },
        "baseUri": {
            "$type": "str",
            "$label": "Key column"
        },
        "keyColumn": {
            "$type": "str",
            "$label": "Key column"
        },
        "column": {
            "$label": "Mapping",
            "$array": true,
            "$object": {
                "$type": "ColumnInfo",
                "name" : {
                    "$type": "str"
                },
                "type": {
                    "$type": "str"
                },
                "lang": {
                    "$type": "str"
                },
                "typeFromDbf": {
                    "$type": "bool"
                },
                "uri" : {
                    "$type": "str"
                }
            }
        },
        "advancedMapping": {
            "$label": "Advanced mapping",
            "$array": true,
            "$object": {
                "$type": "AdvancedMapping",
                "uri" : {
                    "$type": "str"
                },
                "template" : {
                    "$type": "str"
                }
            }
        },
        "namedCell": {
            "$label": "XLS cell mapping",
            "$array": true,
            "$object": {
                "$type": "NamedCell",
                "name" : {
                    "$type": "str"
                },
                "rowNumber" : {
                    "$type": "int"
                },
                "columnNumber" : {
                    "$type": "int"
                }
            }
        },
        "quote": {
            "$type": "str",
            "$label": "Quote char"
        },
        "delimeter": {
            "$type": "str",
            "$label": "Delimiter char"
        },
        "linesToIgnore": {
            "$type": "int",
            "$label": "Skip n first lines"
        },
        "encoding": {
            "$type": "str",
            "$label": "Encoding"
        },
        "rowsLimit": {
            "$type": "int",
            "$label": "Rows limit"
        },
        "tableType": {
            "$type": "str",
            "$label": "Table type"
        },
        "hasHeader": {
            "$type": "bool",
            "$label": "Has header"
        },
        "generateNew": {
            "$type": "bool",
            "$label": "Full column mapping"
        },
        "ignoreBlankCell": {
            "$type": "bool",
            "$label": "Ignore blank cells"
        },
        "advancedKey": {
            "$type": "bool",
            "$label": "Advances key column"
        },
        "rowClass": {
            "$type": "str",
            "$label": "Class for a row entity"
        },
        "sheetName": {
            "$type": "str",
            "$label": "Sheet name"
        },
        "staticRowCounter": {
            "$type": "bool",
            "$label": "Use static row counter"
        },
        "rowTriple": {
            "$type": "bool",
            "$label": "Generate row column"
        },
        "tableSubject": {
            "$type": "bool",
            "$label": "Generate subject for table"
        },
        "autoAsString": {
            "$type": "bool",
            "$label": "Auto type as string"
        },
        "tableClass": {
            "$type": "bool",
            "$label": "Generate table/row class"
        },
        "generateLabels": {
            "$type": "bool",
            "$label": "Generate labels"
        },
        "stripHeader": {
            "$type": "bool",
            "$label": "Strip header for nulls"
        },
        "trimString": {
            "$type": "bool",
            "$label": "Remove trailing spaces"
        },
        "xlsAdvancedParser": {
            "$type": "bool",
            "$label": "Use advanced parser for 'double'"
        },
        "ignoreMissingColumn": {
            "$type": "bool",
            "$label": "Ignore missing columns"
        },
        "generateRowTriple": {
            "$type": "bool",
            "$label": "Generate row column"
        },
        "useDataFormatter" : {
            "$type": "bool",
            "$label": "Format numbers with DateFormatter"
        }
    };

    function controller($scope, $service) {

        if ($scope.dialog === undefined) {
            $scope.dialog = {};
        }

        $scope.onAddColumn = function () {
            $scope.dialog.column.value.push({
                'name': {'value': ''},
                'type': {'value': ''},
                'lang': {'value': ''},
                'typeFromDbf': {'value': false},
                'uri': {'value': ''}
            });
        };

        $scope.onAddAdvancedColumn = function () {
            $scope.dialog.advancedMapping.value.push({
                'uri': {'value': ''},
                'template': {'value': ''}
            });
        };

        $scope.onAddNamedCell = function () {
            $scope.dialog.namedCell.value.push({
                'name': {'value': ''},
                'rowNumber': {'value': 0},
                'columnNumber': {'value': 0}
            });
        };

        $scope.onDelete = function (data, index) {
            data.splice(index, 1);
        };

        const dialogManager = $service.v1.manager(DESC, $scope.dialog);

        $service.onStore = function () {
            dialogManager.save();
        };

        function i18_str(value) {
            if (value === undefined) {
                return undefined;
            }
            if (Array.isArray(value)) {
                const result = [];
                value.forEach((item) => {
                    result.push(item["@value"]);
                });
                return result;
            } else if (value["@value"]) {
                return value["@value"];
            } else {
                return value;
            }
        }

        dialogManager.load();

        // Load rowsClass to rowClass if rowClass is not set.
        if ($scope.dialog.rowClass.value === undefined) {
            const instanceTriples = jsonld.triples($service.config.instance);
            const prefix = 'http://plugins.linkedpipes.com/ontology/t-tabularUv#';
            const resource = instanceTriples.getResource(
                instanceTriples.findByType(prefix + 'Configuration'))
            $scope.dialog.rowClass.value =
                i18_str(jsonld.r.getString(resource, prefix + 'rowsClass'));
        }
    }

    controller.$inject = ['$scope', '$service'];
    return controller;
});
