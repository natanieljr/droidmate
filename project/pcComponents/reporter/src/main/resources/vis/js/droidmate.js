/**
 * Mostly inspired by: https://github.com/honeynet/droidbot
 */
var network = null;

function draw() {
	var graphcontainer = document.getElementById('graphcontainer');
	var details_container = document.getElementById('details_container');

	showOverall();

	var options = {
		autoResize: true,
		height: '100%',
		width: '100%',
		locale: 'en',
		physics: {
			enabled: false
		},
		nodes: {
			shapeProperties: {
				useBorderWithImage: true
			},
			borderWidth: 0,
			borderWidthSelected: 5,
			color: {
				border: '#FFFFFF',
				background: '#FFFFFF',

				highlight: {
					border: '#0000FF',
					background: '#0000FF'
				}
			},
			font: {
				size: 12,
				color:'#000'
			}
		},
		edges: {
			color: 'black',
			length: 200,
			arrows: {
				to: {
					enabled: true,
					scaleFactor: 0.5
				}
			},
			font: {
				size: 12,
				color:'#000'
			}
		}
	};

	network = new vis.Network(graphcontainer, data, options);

	network.on("click", function (params) {
		if (params.nodes.length > 0) {
			var node = params.nodes[0];
			if (network.isCluster(node)) {
				details_container.innerHTML = getClusterDetails(node);
			}
			else {
				details_container.innerHTML = getNodeDetails(node);
			}
		} else if (params.edges.length > 0) {
			var edge = params.edges[0];
			var baseEdge = network.clustering.getBaseEdge(edge);
			if (baseEdge == null || baseEdge == edge) {
				details_container.innerHTML = getEdgeDetails(edge);
			} else {
				details_container.innerHTML = getEdgeDetails(baseEdge);
			}
		}
	});
}

function showOverall() {
	var details_container = document.getElementById('details_container');
	details_container.innerHTML = getOverallResult();
}

function getOverallResult() {
	var overallInfo = "<hr />";
	overallInfo += "<table class=\"table\">\n";

	overallInfo += "<tr class=\"active\"><th colspan=\"2\"><h4>Execution result</h4></th></tr>\n";
	overallInfo += "<tr><th class=\"col-md-1\">explorationStartTime</th><td class=\"col-md-4\">" + data.explorationStartTime + "</td></tr>\n";
	overallInfo += "<tr><th class=\"col-md-1\">explorationEndTime</th><td class=\"col-md-4\">" + data.explorationEndTime + "</td></tr>\n";
	overallInfo += "<tr><th class=\"col-md-1\">explorationTimeInMs</th><td class=\"col-md-4\">" + data.explorationTimeInMs + "</td></tr>\n";
	overallInfo += "<tr><th class=\"col-md-1\">numberOfActions</th><td class=\"col-md-4\">" + data.numberOfActions + "</td></tr>\n";
	overallInfo += "<tr><th class=\"col-md-1\">numberOfStates</th><td class=\"col-md-4\">" + data.numberOfStates + "</td></tr>\n";

	overallInfo += "<tr class=\"active\"><th colspan=\"2\"><h4>Apk information</h4></th></tr>\n";
	overallInfo += "<tr><th class=\"col-md-1\">path</th><td class=\"col-md-4\">" + data.apk.path + "</td></tr>\n";
	overallInfo += "<tr><th class=\"col-md-1\">packageName</th><td class=\"col-md-4\">" + data.apk.packageName + "</td></tr>\n";
	overallInfo += "<tr><th class=\"col-md-1\">launchableActivityName</th><td class=\"col-md-4\">" + data.apk.launchableActivityName + "</td></tr>\n";
	overallInfo += "<tr><th class=\"col-md-1\">launchableActivityComponentName</th><td class=\"col-md-4\">" + data.apk.launchableActivityComponentName + "</td></tr>\n";
	overallInfo += "<tr><th class=\"col-md-1\">applicationLabel</th><td class=\"col-md-4\">" + data.apk.applicationLabel + "</td></tr>\n";
	overallInfo += "<tr><th class=\"col-md-1\">fileName</th><td class=\"col-md-4\">" + data.apk.fileName + "</td></tr>\n";
	overallInfo += "<tr><th class=\"col-md-1\">fileNameWithoutExtension</th><td class=\"col-md-4\">" + data.apk.fileNameWithoutExtension + "</td></tr>\n";
	overallInfo += "<tr><th class=\"col-md-1\">absolutePath</th><td class=\"col-md-4\">" + data.apk.absolutePath + "</td></tr>\n";
	overallInfo += "<tr><th class=\"col-md-1\">inlined</th><td class=\"col-md-4\">" + data.apk.inlined + "</td></tr>\n";
	overallInfo += "<tr><th class=\"col-md-1\">instrumented</th><td class=\"col-md-4\">" + data.apk.instrumented + "</td></tr>\n";
	overallInfo += "<tr><th class=\"col-md-1\">isDummy</th><td class=\"col-md-4\">" + data.apk.isDummy + "</td></tr>\n";

	overallInfo += "</table>";
	return overallInfo;
}

function getEdgeDetails(edgeId) {
	var selectedEdge = getEdge(edgeId);
	var detailTable = '<table class=\"table\">\n';
	detailTable += "<tr><th class=\"col-md-1\">from</th><td class=\"col-md-4\">" + selectedEdge.from + "</td></tr>\n";
	detailTable += "<tr><th class=\"col-md-1\">to</th><td class=\"col-md-4\">" + selectedEdge.to + "</td></tr>\n";
	detailTable += "<tr><th class=\"col-md-1\">actionType</th><td class=\"col-md-4\">" + selectedEdge.actionType + "</td></tr>\n";
	detailTable += '</table>';

	var edgeInfo = "<h2>Transition Details</h2><hr/>\n";
	var fromState = getNode(selectedEdge.from);
	var toState = getNode(selectedEdge.to);
	edgeInfo += "<img class=\"col-md-5\" src=\"" + fromState.image + "\">\n";
	edgeInfo += "<div class=\"col-md-2 text-center\">TO</div>\n";
	edgeInfo += "<img class=\"col-md-5\" src=\"" + toState.image + "\">\n";
	edgeInfo += detailTable;
	edgeInfo += "<table class=\"table table-striped\">\n";
	edgeInfo += "<tr class=\"active\"><th colspan=\"4\"><h4>TargetWidgets</h4></th></tr>\n";

	var i;
	edgeInfo += "<tr><th>Nr. of action</th><th>id</th><th>text</th><th>contentDesc</th></tr>\n";
	for (i = 0; i < selectedEdge.targetWidgets.length; i++) {
		var targetWidget = selectedEdge.targetWidgets[i];
		// TODO insert here image, when the creation of the target widgets image is fixed in DroidMate
		// var viewImg = "";
		// if (event.view_images != null) {
		// 	var j;
		// 	for (j = 0; j < event.view_images.length; j++) {
		// 		viewImg += "<img class=\"viewImg\" src=\"" + event.view_images[j] + "\">\n"
		// 	}
		// }
		// edgeInfo += "<tr><td>" + event.event_id + "</td><td>" + event.event_type + "</td><td>" + viewImg + "</td><td>" + event.event_str + "</td></tr>"
		edgeInfo += "<tr><td>" + targetWidget.idxOfAction + "</td><td>" + targetWidget.id + "</td><td>" + targetWidget.text + "</td><td>" + targetWidget.contentDesc + "</td></tr>"
	}
	edgeInfo += "</table>\n";

	return edgeInfo;
}

function getNodeDetails(nodeId) {
	var node = getNode(nodeId);
	var detailTable = '<table class=\"table\">\n';
	detailTable += "<tr><th class=\"col-md-1\">stateId</th><td class=\"col-md-4\">" + node.stateId + "</td></tr>\n";
	detailTable += "<tr><th class=\"col-md-1\">uid</th><td class=\"col-md-4\">" + node.uid + "</td></tr>\n";
	detailTable += "<tr><th class=\"col-md-1\">configId</th><td class=\"col-md-4\">" + node.configId + "</td></tr>\n";
	detailTable += "<tr><th class=\"col-md-1\">topNodePackageName</th><td class=\"col-md-4\">" + node.topNodePackageName + "</td></tr>\n";
	detailTable += "<tr><th class=\"col-md-1\">iEditId</th><td class=\"col-md-4\">" + node.iEditId + "</td></tr>\n";
	detailTable += "<tr><th class=\"col-md-1\">hasEdit</th><td class=\"col-md-4\">" + node.hasEdit + "</td></tr>\n";
	detailTable += "<tr><th class=\"col-md-1\">isHomeScreen</th><td class=\"col-md-4\">" + node.isHomeScreen + "</td></tr>\n";
	detailTable += '</table>';

	var stateInfo = '<h2>State Details</h2><hr/>\n';
	stateInfo += '<img class=\"col-md-5\" src=\"' + node.image + '\">';
	stateInfo += '<div class=\"col-md-7\">' + detailTable + '</div>';
	return stateInfo;
}

function getClusterDetails(clusterId) {
	var clusterInfo = "<h2>Cluster Details</h2><hr/>\n";
	var nodeIds = network.getNodesInCluster(clusterId);
	for (var i = 0; i < nodeIds.length; i++) {
		var selectedNode = getNode(nodeIds[i]);
		clusterInfo += "<div class=\"row\">\n";
		clusterInfo += "<img class=\"col-md-5\" src=\"" + selectedNode.image + "\">";
		clusterInfo += "<div class=\"col-md-7\">" + selectedNode.title + "</div>";
		clusterInfo += "</div><br />"
	}
	return clusterInfo;
}

function getEdge(edgeId) {
	var i, numEdges;
	numEdges = data.edges.length;
	for (i = 0; i < numEdges; i++) {
		if (data.edges[i].id == edgeId) {
			return data.edges[i];
		}
	}
	console.log("cannot find edge: " + edgeId);
}

function getNode(nodeId) {
	var i, numNodes;
	numNodes = data.nodes.length;
	for (i = 0; i < numNodes; i++) {
		if (data.nodes[i].id == nodeId) {
			return data.nodes[i];
		}
	}
	console.log("cannot find node: " + nodeId);
}

function showAbout() {
	var details_container = document.getElementById('details_container');
	details_container.innerHTML = getAboutInfo();
}

function getAboutInfo() {
	var aboutInfo = "<hr />";
	aboutInfo += "<h2>About</h2>\n";
	aboutInfo += "<p>This report is generated using <a href=\"https://github.com/honeynet/droidbot\">DroidBot</a>.</p>\n";
	aboutInfo += "<p>Please find copyright information in the project page.</p>";
	return aboutInfo;
}

function searchGraph() {
	var searchKeyword = document.getElementById("searchBar").value.toUpperCase();
	if (searchKeyword == null || searchKeyword == "") {
		network.unselectAll()
	} else {
		var i, numNodes;
		var nodes = data.nodes;
		numNodes = nodes.length;
		var selectedNodes = [];
		for (i = 0; i < numNodes; i++) {
			if (nodes[i].content.toUpperCase().indexOf(searchKeyword) > -1) {
				selectedNodes.push(nodes[i].id)
			}
		}
		network.unselectAll();
		// console.log("Selecting: " + selectedNodes)
		network.selectNodes(selectedNodes, false);
	}
}
