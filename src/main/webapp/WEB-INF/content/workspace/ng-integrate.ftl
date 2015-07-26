<#setting url_escaping_charset="UTF-8">
<#import "/WEB-INF/macros/resource/common.ftl" as common>

<head>
    <title>Dataset Integration: Edit</title>
</head>
<body>
<div id="divIntegrationMain" ng-controller="IntegrationController as ctrl">
    <div id="divIntegrationHeader">
        <h1 class="compact">Dataset Integration</h1>
    </div>
    <div id="divStatusMessage" class="alert alert-{{alert.kind}}" ng-show="alert.message !==''" >
    {{alert.message}}
    </div>

    <form id="frmIntegrationEdit" class="form-horizontal" ng-init="ctrl.loadJSON()">
        <div class="row">
            <div class="span9">
               <div class="control-group">
                   <label class="control-label">
                       Integration Name
                   </label>
                   <div class="controls">
                       <input type="text" class="input-block-level" name="integration.title" ng-model="ctrl.integration.title">
                   </div>
               </div>
               <div class="control-group">
                   <label class="control-label">Description</label>
                   <div class="controls">
                       <textarea name="integration.description" class="input-block-level" cols="80" rows="2"
                                 ng-model="ctrl.integration.description"></textarea>
                   </div>
               </div>
            </div>
            <div class="span3">
                <button type="button" class="btn btn-primary" ng-disabled="!isValid()" id="btnIntegrate" ng-click="ctrl.submitIntegration()">Integrate</button>
                <#--<button type="button" class="btn btn-primary" ng-disabled="!isValid()" id="btnIntegrate" ng-click="ctrl.integrateClicked()">Integrate</button>-->

                <!-- re enable ignore-ng-disabled when TDAR-4367 is fixed -->
                <!-- Split button -->
                <div class="btn-group">
                  <button type="button" class="btn" ignore-ng-disabled="!isMinimallyValid()" ng-disabled="!isValid()"  id="btnSave" ng-click="ctrl.saveClicked()">Save</button>
                  <#--
                  <button type="button" class="btn dropdown-toggle" data-toggle="dropdown" aria-expanded="false">
                    <span class="caret"></span>
                  </button>
                  <ul class="dropdown-menu" role="menu">
                    <li>
                    <a ignore-ng-disabled="!isMinimallyValid()" ng-disabled="!isValid()"  id="btnSaveAs" ng-click="ctrl.saveAsClicked()">Save As</a>
                  </ul>
                  -->
                </div>
            </div>
        </div>

        <div id="divSelectedItemsSection">
            <div class="row">
                <div class="span12">
                    <div class="control-group">
                        <label class="control-label">Datasets & Ontologies</label>
                        <div class="controls controls-row">
                            <div class="span4">
                                <label><b>Selected Datasets</b></label>
                                <div>
                                    <select size="5" class="input-block-level" multiple
                                            ng-model="selectedDataTables" name="selectedDatasets" id="selDatasets"
                                            ng-options="dataTable|dtDisplayName|titleCase for dataTable in ctrl.integration.dataTables"></select>
                                </div>
                                
                                
<div class="controls controls-row">
                                <div class="btn-group">
                                    <button type="button" class="btn"  id="btnAddDataset"
                                            ng-click="ctrl.addDatasetsClicked()">Add Datasets...</button>
                                    
                                    <button type="button" class="btn" id="rmDatasetBtn" 
                                            ng-click="ctrl.removeSelectedDatasetClicked()" ng-disabled="ctrl.integration.dataTables.length === 0">Remove selected dataset</button>
                                </div>
                        </div>
                        </div>
                            <div class="span4">
                                <label><b>Shared Ontologies</b></label>
                                <ul>
                                    <li class="sharedOntologies" ng-repeat="ontology in ctrl.integration.ontologies">{{ontology | ontDisplayName}}</li>
                                </ul>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        </form>
        <form>
                <div id="divActionsSection">
                    <div class="control-group">
                        <label class="control-label">Actions</label>
                        <div class="controls">
                            <div class="btn-group">
                                <div class="btn-group" >
                                    <a class="btn dropdown-toggle" data-toggle="dropdown" href="#" ng-class="{disabled: !ctrl.integration.ontologies.length}">
                                        Add Integration Column
                                        <span class="caret"></span>
                                    </a>
                                    <ul class="dropdown-menu" id="btnSetAddIntegrationColumns">
                                        <li ng-repeat="ontology in ctrl.integration.ontologies"
                                                ><a ng-click="ctrl.addIntegrationColumnsMenuItemClicked(ontology)">{{ontology.title}}</a></li>
                                    </ul>
                                </div>
                                <button type="button" class="btn" id="btnAddDisplayColumn"
                                        ng-click="ctrl.addDisplayColumnClicked()"
                                        ng-disabled="!ctrl.integration.ontologies.length"
                                        >Add Display Column</button>

                                <button type="button" class="btn" id="btnAddCountColumn"
                                        ng-click="ctrl.addCountColumnClicked()"
                                        ng-disabled="ctrl.isCountColumnDisabled()"
                                        >Add Count Column</button>
                            </div>

                        </div>
                    </div>
        </div>
        
        <div id="divColumnSection">
            <div class="row">
                <div class="span12">

                    <div class="control-group" ng-show="ctrl.integration.columns.length">
                        <label class="control-label">
                            Configure Columns
                        </label>
                        <div class="controls">

                            <div id="tabControl">
                                <ul class="nav nav-tabs">
                                    <li ng-repeat="column in ctrl.integration.columns" ng-click="ctrl.setTab($index)" onclick="return false;" ng-class="{active: ctrl.isTabSet($index)}" >
                                        <a href="#tab{{$index}}" id="tabtab{{$index}}">
                                        {{column.name}}
                                            <input type="hidden" name="column.name{{$index}}" ng-model="column.name" />
                                            <button class="close" ng-click="ctrl.closeTab($index)">x</button>
                                        </a>
                                    </li>
                                </ul>

                                <div class="tab-content" >
                                    <div class="tab-pane" id="tab{{$index}}"
                                         ng-repeat="outputColumn in ctrl.integration.columns" ng-class="{active: ctrl.isTabSet($index)}">
                                        <div ng-switch="outputColumn.type">
                                            <div ng-switch-when="integration" class=".integration-pane-content">
                                                <div class="alert" ng-hide="outputColumn.isValidMapping">
                                                    <strong>Invalid Ontology</strong> {{outputColumn.ontology | ontDisplayName}} does not belong to a shared ontology.
                                                </div>
                                                <table class="table table-bordered table-condensed table-hover">
                                                    <thead>
                                                    <tr>
                                                        <th rowspan="2" style="white-space: nowrap;">&nbsp;</th>
                                                        <th rowspan="2" style="width:99%">
                                                            <div class="pull-right">
                                                                Select values that appear in
                                                                <div class="btn-group">
                                                                    <button class="btn btn-mini" type="button" ng-click="selectMatchingNodes('some')">Any column</button>
                                                                    <button class="btn btn-mini" type="button" ng-click="selectMatchingNodes('every')">Every column</button>
                                                                </div>
                                                            </div>
                                                            <div>Node Value</div>
                                                        </th>
                                                        <th rowspan="1" style="white-space: nowrap;" colspan="{{outputColumn.dataTableColumns.length}}">
                                                            Datasets
                                                        </th>
                                                    </tr>
                                                    <tr>
                                                        <th ng-repeat="cc in lookupCompatibleColumns(outputColumn.ontologyId)" >
                                                            <!-- suggest using  track by c.name to get at a key that we can more easily use" -->
                                                            <div ng-switch on="cc.compatCols.length">
                                                            <div ng-switch-when="1">
                                                                <span title="{{cc.dataTable.datasetTitle}} :: {{cc.dataTable.displayName}}">{{cc.compatCols[0].displayName}}</span>
                                                                <!-- FIXME: this is "hidden", but is it even needed? -->
                                                                <!-- FIXME: shouldn't this be the dataset name? -->
                                                                <select class="intcol" ng-model="outputColumn.selectedDataTableColumns[$index]" ng-options="c.displayName for c in cc.compatCols" ng-hide="true"></select>
                                                            </div>
                                                            <div ng-switch-default>
                                                                <select title="{{cc.dataTable.datasetTitle}} :: {{cc.dataTable.displayName}}" class="intcol" ng-model="outputColumn.selectedDataTableColumns[$index]" ng-options="c.displayName for c in cc.compatCols"></select>
                                                            </div>
                                                            </div>
                                                        </th>
                                                    </tr>
                                                    </thead>
                                                    <tbody>
                                                    <tr ng-repeat="nodeSelection in outputColumn.nodeSelections" ng-init="nodeIndex = $index">
                                                        <td><input type="checkbox" name="cbont" ng-model="nodeSelection.selected" id="cbont_{{::nodeSelection.node.id}}"></td>
                                                        <td style="white-space: nowrap;">
                                                            <div class="nodechild{{::nodeSelection.node.index.split('.').length}}">
                                                                <label for="cbont_{{::nodeSelection.node.id}}">{{::nodeSelection.node.displayName}}</label>
                                                            </div>
                                                        </td>
                                                        <td ng-repeat="dataTableColumn in outputColumn.selectedDataTableColumns">
                                                            <div class="text-center">
                                                                <i class="icon-ok" id="cbx-{{::dataTableColumn.id}}-{{::nodeSelection.node.id}}" ng-show="::ontologyValuePresent(dataTableColumn, nodeSelection.node)"></i>
                                                            </div>
                                                        </td>
                                                    </tr>
                                                    </tbody>
                                                </table>
                                            </div>

                                            <div ng-switch-when="display" class=".display-pane-content">
                                            
                                                <h3>Choose the source columns to include in this display column </h3>
                                                <table class="table">
                                                    <thead>
                                                        <tr>
                                                            <th>Dataset</th>
                                                            <th>Table</th>
                                                            <th>Column</th>
                                                        </tr>
                                                    </thead>
                                                    <#--<tr ng-repeat="columnSelection in outputColumn.dataTableColumnSelections">-->
                                                    <tr ng-repeat="dataTable in ctrl.integration.dataTables" ng-init="columnSelection = outputColumn.dataTableColumnSelections[$index]">
                                                        <td class="">{{dataTable.datasetTitle}}</td>
                                                        <td class="">{{dataTable.displayName}}</td>
                                                        <td>
                                                            <select ng-model="columnSelection.dataTableColumn" id="dt_{{$parent.$index}}_{{dataTable.id}}"
                                                                    ng-options="c.displayName for c in dataTable.dataTableColumns  | orderBy: 'displayName' ">
                                                                <option value="" class="emptyoption">No column selected</option>
                                                            </select>
                                                        </td>
                                                    </tr>

                                                </table>
                                            </div>


                                            <div ng-switch-when="count" class=".count-pane-content">
                                                <h3>Select count columns </h3>
                                                <table class="table">
                                                    <thead>
                                                        <tr>
                                                            <th>Dataset</th>
                                                            <th>Table</th>
                                                            <th>Column</th>
                                                        </tr>
                                                    </thead>
                                                    <#--<tr ng-repeat="columnSelection in outputColumn.dataTableColumnSelections">-->
                                                    <tr ng-repeat="dataTable in ctrl.integration.dataTables" ng-init="columnSelection = outputColumn.dataTableColumnSelections[$index]">
                                                        <th class="">{{dataTable.datasetTitle}}</th>
                                                        <th class="">{{dataTable.displayName}}</th>
                                                        <td>
                                                            <select ng-model="columnSelection.dataTableColumn"
                                                                    ng-options="c.displayName for c in dataTable.dataTableColumns  | orderBy: 'displayName'  | filter:filterCount">
                                                                <option value="" class="emptyoption">No column selected</option>
                                                            </select>
                                                        </td>
                                                    </tr>

                                                </table>
                                            </div>


                                        </div>
                                    </div>
                                </div>
                            </div>

                        </div>
                    </div>
            </div>
        </div>
    </form>
</div>

<tdar-modal ng-controller="ModalDialogController"></tdar-modal>

<!-- Note: this modal is about span10 wide. Form-horizontal labels are ~span3 wide, leaving you ~span7 for controls. -->
<script type="text/ng-template" id="workspace/modal-dialog.html">
    <div id="divModalContainer" class="modal modal-big hide fade" tabindex="-1" role="dialog">
        <div class="modal-header alert-info">
            <button type="button" class="close" data-dismiss="modal" aria-hidden="true">×</button>
            <div id="#modalAjaxIndicator" class="pull-right">
                <span class="small" ng-show="modalSearching">Searching</span>
            </div>
            <h3 id="hModalHeader">{{title}}</h3>
        </div>
        <div class="modal-body">
            <div class="row-fluid">
                <div class="span12">
                    <form id="frmModal" class="form-horizontal form-condensed" ng-model-options="{ updateOn: 'default blur', debounce: {'default': 500, 'blur':0, 'click':0} }">
                        <div>
                            <div class="control-group">
                                <label class="control-label">Title contains</label>
                                <div class="controls">
                                    <input type="text" ng-model="filter.title" class="input-block-level" name="searchFilter.title" ng-change="updateFilter()"> </input>
                                </div>
                            </div>
                            <div class="control-group">
                                <label class="control-label">Belongs to</label>
                                <div class="controls controls-row">
                                    <div class="span3">
                                        <select name="searchFilter.projectId" class="input-block-level"
                                                ng-model="filter.projectId" ng-options="project.id as project.title for project in projects"
                                                ng-change="updateFilter()">
                                            <option value="">Any project</option>
                                        </select>
                                    </div>
                                    <div class="span3">
                                        <select name="searchFilter.collectionId" class="input-block-level"
                                                ng-model="filter.collectionId" ng-options="collection.id as collection.title for collection in collections"
                                                ng-change="updateFilter()">
                                            <option value="">Any collection</option>
                                        </select>
                                    </div>
                                </div>
                            </div>

                            <div class="control-group" ng-show="categoryFilter">
                                <label class="control-label">Category</label>
                                <div class="controls">
                                    <select name="searchFilter.categoryId" class="input-xlarge"
                                            ng-model="filter.categoryId" ng-options="category.id as category.name group by category.parent_name for category in categories"
                                            ng-change="updateFilter()">
                                        <option value="">Any category</option>
                                    </select>
                                </div>
                            </div>

                            <div class="control-group">
                                <label class="control-label">Other Filters</label>
                                <div class="controls form-inline">
                                    <label class="checkbox inline"><input type="checkbox" name="searchFilter.bookmarked" ng-model="filter.bookmarked" ng-change="updateFilter()">Bookmarked Items</label>
                                    <label class="checkbox inline"><input type="checkbox" name="searchFilter.integrationCompatible" ng-model="filter.integrationCompatible" value="true" ng-change="updateFilter()">Integration-compatible</label>
                                </div>
                            </div>
                        </div>

                        <div class="table-modal-results-container">
                            <table class="table table-striped table-modal-results table-hover table-condensed" id="modalResults" ng-class="{active: !modalSearching, disabled: modalSearching}">
                                <thead>
                                <tr>
                                    <th style="width:1em">&nbsp</th>
                                    <th style="width:40em">Title</th>
                                    <th style="width:10em">Date</th>
                                    <th style="width: 20em">Mapped Ontologies</th>
                                </tr>
                                </thead>
                                <tbody>
                                <tr ng-repeat="result in results" ng-class="{warning: isSelected(result.id)}">
                                    <td style="width:1em">
                                        <input
                                                type="checkbox"
                                                id="cbResult{{result.id}}"
                                                name="selectedItems[]"
                                                value="{{result.id}}"
                                                ng-enabled="!modalSearching"
                                                ng-checked="isSelected(result.id)"
                                                ng-click="toggleSelection(result.id, this)">
                                    </td>
                                    <td style="width:40em"><label for="cbResult{{result.id}}">{{result.title}}</label></td>
                                    <td style="width:10em">{{result.date_created | date }}</td>
                                    <td class="ellipsified" style="max-width: 20em">
                                        <span ng-repeat="ontology in result.ontologies">{{$first ? '' : ', '}}{{ontology}}</span>
                                    </td>
                                </tr>
                                </tbody>
                            </table>
                        </div>
                    </form>
                </div>
            </div>
        </div>
        <div class="modal-footer">
            <div class="row-fluid">
                <div class="span4 text-left">
                    <span ng-show="results.length"> Displaying records {{startRecord() + 1| number}} - {{endRecord()  | number}} of {{modalTotalResults}} </span>
                </div>
                <div class="span4 text-center">
                    <button type="button" class="btn btn-mini" id="btnPrevious" ng-click="previousPage()" ng-disabled="!hasPreviousPage()">previous</button>
                    <button type="button" class="btn btn-mini" id="btnNext" ng-click="nextPage()" ng-disabled="!hasNextPage()">next</button>

                </div>
                <div class="span4">
                    <ng-pluralize count="selectedItems.length"
                                  when="{'0': 'No datasets selected',
                                '1': '1 dataset selected',                                'other': '{{selectedItems.length}} datasets selected'}"></ng-pluralize>
                    <span ng-show="selectedItems.length" >(<a href="javascript:void(0)"  ng-click="clearSelectedItems()">clear selections</a>)</span>
                </div>
            </div>





            <div class="row-fluid">
                <div class="span12">
                    <button class="btn" data-dismiss="modal" aria-hidden="true" ng-click="cancel()">Close</button>
                    <button class="btn btn-primary" id="btnModalAdd" data-dismiss="modal" ng-click="confirm(selectedItems)">Add selected items</button>
                </div>
            </div>



        </div>
    </div>
</script>
<script type="application/json" id="jsondata">
${workflowJson!"{}"}
</script>

<!-- FIXME: embedded lookup data like this will be untenable for large datasets - use ajax solution instead -->
<!-- FIXME: too much crap - we just need ID and title and submitterId -->
<script type="application/json" id="allProjects">
${fullUserProjectsJson}
</script>

<script type="application/json" id="allCollections">
${allResourceCollectionsJson}
</script>

<script type="application/json" id="allCategories">
${categoriesJson}
</script>
<script src='https://ajax.googleapis.com/ajax/libs/angularjs/1.3.0/angular.min.js'></script>

<#-- fixme: hack: /workspace/integrate#addDatasets  -->
<script>
    console.warn("Tell jim to remove his auto-open hack");
    $(function() {
        if(window.location.hash === "#addDatasets") {
            $("#btnAddDataset").click();
        }
    })
</script>


<div>

    <div id="divResultContainer" class="modal modal-big fade hide" tabindex="-1" role="dialog">

        <div class="modal-header alert-info">
            <button type="button" class="close" data-dismiss="modal" aria-hidden="true">×</button>
            <h3 id="hModalHeader">Integration Results</h3>
        </div>
        <div class="modal-body">
            <div class="row-fluid">
                <div class="span12">
                    
                    <div role="tabpanel">
                    
                      <!-- Nav tabs -->
                      <ul class="nav nav-tabs" role="tablist">
                        <li role="presentation" class="active"><a href="#pivot" aria-controls="pivot" role="tab" data-toggle="tab">Summary</a></li>
                        <li role="presentation"><a href="#preview" aria-controls="preview" role="tab" data-toggle="tab">Preview</a></li>
                      </ul>
                    
                      <!-- Tab panes -->
                      <div class="tab-content">
                        <div role="tabpanel" class="tab-pane active" id="pivot">
                            <table tdar-datatable aa-data="download.pivotData.rows" ao-columns="download.pivotData.columns" id="tblPivotData"></table>
                        </div>
                        <div role="tabpanel" class="tab-pane" id="preview">
                            <table tdar-datatable aa-data="download.previewData.rows" ao-columns="download.previewData.columns" id="tblPreviewData"></table>
                        </div>
                      </div>
                    
                    </div>
                </div>
            </div>
        </div>
        <div class="modal-footer">

            <div class="row-fluid">
                <div class="span12">
                    <a type="button" class="btn" ng-href="/workspace/download?ticketId={{download.ticketId}}">Download</a>
                    <button class="btn" data-dismiss="modal" aria-hidden="true" ng-click="cancel()">Close</button>
                </div>
            </div>
        </div>
    </div>
</div>
</body>
