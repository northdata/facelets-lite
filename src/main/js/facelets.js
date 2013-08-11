/*************** WARNING EXPERIMENTAL *************************/

"use strict";

var parseXml = function() {
	if (typeof window.DOMParser != "undefined") {
	    return function(xmlStr) {
	        return ( new window.DOMParser() ).parseFromString(xmlStr, "text/xml");
	    };
	} 
	if (typeof window.ActiveXObject != "undefined" && new window.ActiveXObject("Microsoft.XMLDOM")) {
	    return function(xmlStr) {
	        var xmlDoc = new window.ActiveXObject("Microsoft.XMLDOM");
	        xmlDoc.async = "false";
	        xmlDoc.loadXML(xmlStr);
	        return xmlDoc;
	    };
	} 
	throw new Error("No XML parser found");
}()

var Namespaces = {
	Core: "http://java.sun.com/jsp/jstl/core",
	UI: "http://java.sun.com/jsf/facelets"
};

var Evaluator = function(context) {
	//console.info("preparing context", context);
	var exact = /^\#\{[^\}]*\}$/
	var search = /\#\{([^\}]*)\}/g
	var keys = [];
	var values = [];
	for (var key in context) {
		keys.push(key);
		values.push(context[key]);
	}
	var evalExpr = function(expr) {
		expr = expr.substring(2, expr.length-1);
		var original = expr;
		expr = expr.replace("empty", "!!");
		//console.info("evaluating", expr);
		var evalString = "(function("+keys.join(",")+"){return "+expr+"})";
		try
		{
			return eval(evalString).apply({}, values); // this ???
		}
		catch (error) {
			console.error("cannot evaluate", original);
			throw error;
		}
	}
	return function(string) {
		return string.match(exact) ? evalExpr(string) : string.replace(search, evalExpr);
	}
}

var Facelet = function(facelets, text) {
	var xml = parseXml(text);
	var process = function(context) {
		console.info("processing: context is", context);
		var tasks = [];
		var result;
		var createRoot = function() {
			return result = document.createElement("div");
		}
		createRoot();
		var push = function(context, name, value, name1, value1) {
			if (!name && !name1) {
				return context;
			}
			var result = {};
			for (var key in context) {
				result[key] = context[key];
			}
			if (name) { result[name] = value; }
			if (name1) { result[name1] = value1; }
			return result;
		}
		var handleLater = function(nodes, targetParent, context) { 
			// take pressure of native stack by keeping our own:
			tasks.push( function() { handle(nodes, targetParent, context)} )
		}
		var handle = function(nodes, targetParent, context) {
			var evaluator;
			var evaluate = function(string) {
				evaluator = evaluator || Evaluator(context);
				return evaluator(string);
			}
			var attr = function(element, name) {
				var value = element.getAttribute(name);
				return value && evaluate(value);
			}
			var requiredAttr = function(element, name) {
				var result = attr(element, name);
				if (result==undefined) { throw new Error("attribute '"+name+"' required in "+element.tagName+" element")}
				return result;
			}
			var handleCoreTag = function(tagName, element) 
	    	{
				switch (tagName) {
				case "set":
			        context[requiredAttr(element, "var")] = requiredAttr(element, "value");
			        //console.info("changed context", context);
			        evaluator =  null; // 
			        break;
				case "if":
					var test = requiredAttr(element, "test");
					var name = attr(element, "var");
					//console.info("test condition ", test, " of type ",typeof test," treated as ", !!test);
					if (test) {
						//console.info("insert ", element.childNodes, targetParent, push(context, name, test));
						handle(element.childNodes, targetParent, push(context, name, test));
					}
					break;
				case "forEach": case "foreach": 
			        var items = attr(element, "items") || [];
			        if (!Array.isArray(items)) {
			        	console.error("found ", items, " as items in ", element);
			        	throw new Error("items is not an array", items);
			        }
			        var name = attr(element, "var");
			        var varStatus = attr(element, "varStatus");
			        var begin = attr(element, "begin") || 0;
			        var end = attr(element, "end") || items.length-1;
			        var step = attr(element, "step") || 1;
			        var count = 0;
			        //console.info("iterating",name,"from",begin,"to",end);
			        for (var i=begin; i<=end && items[i]; i+=step) {
			        	++count;
			        	var status = {
			        		begin: begin,
			        		end: end,
			        		step: step,
			        		index: i,
			        		first: i==begin,
			        		last: i==end,
			        		count: count,
			        		current: items[i]
			        	}
			        	handle(
			        		element.childNodes,
			        		targetParent,
			        		push(context, name, items[i], varStatus, status)
			        	)
			        }
			        break;
				case "choose":
					var prefix = element.lookupPrefix.get(Namespaces.Core);
					var whens = element.getElementsByTagNameNS(Namespaces.Core, "when");
					for (var i=0; i<whens.length; ++i) {
						var test = requiredAttr(when, "test");
						if (test) {
							handle(when.childNodes, targetParent, context);
							return;
						}
					}
					var otherwises =  element.getElementsByTagNameNS(Namespaces.Core, "otherwise");
					if (otherwises.size()>0) {
						handle(otherwises[0].childNodes, targetParent, context);
					}
					break;
				case "catch":
					throw new Error("not supported");
				default: 
					throw new Error("invalid core tag name '"+tagName+"'");
				}
	    	}
	    		
			var handleUiTag = function(tagName, element) 
	    	{
	    		var prefix = element.lookupPrefix(Namespaces.UI);
	    		if ("include" == tagName) {
	    			// TODO
	    		}
	    		if ("insert"== tagName) {
	    			// TODO
	    		}
	    		if ("decorate"== tagName) {
	    			// TODO
	    		}
	    		if ("composition"== tagName) {
	    			targetParent = createRoot();
	    			handle(element.childNodes, targetParent, context);
	    			return;
	    		}
	    		if ("debug"== tagName) {
	    			log.warn("ignoring ui debug tag");
					return;
				}
	    		if ("remove"== tagName) {
	    			// nothing
					return;
				}
	    		if ("param"== tagName) {
	    			throw new Error("only allowed as child of :include tag");
				}
	    		if ("define"== tagName) {
	    			throw new Error("only allowed as child of :composition or :decorate tag");
				}
	    		throw new Error("invalid ui tag name '"+tagName+"'");
	    	}
			var handleCustomTag = function(prefix, tagName, node) {
    			var facelet = facelets.get(tagName);
    			var context = {}; 
				var attributes = node.attributes;
				for (var j=0; j<node.attributes.length; ++j) {
					var attribute = node.attributes[j];
					var value = attribute.value;
					if (!attribute.namespaceURI) {
    					var newValue = evaluate(attribute.value);
    					if (newValue !== undefined) {
    						context[attribute.name] = newValue;
    					}
					}
				}
				console.info(node, "evaluates to");
    			var children = facelet.renderAndReturnNodes(context);
    			console.info(children.length, children);
    			for (var i=0; i<children.length; ++i) {
    				var child = children[i];
    				console.info("child["+i+"]", child, !!child);
    				if (child) {
	    				targetParent.appendChild(child);
	    				console.info("appending", child, "to", targetParent);
    				}
				}
				//throw new Error("not yet supported");
			}
			//console.info("generating children of ", targetParent.outerHTML);
			
			for (var i=0; i<nodes.length; ++i) {
				var node = nodes[i];
				//console.info(" -> processing node", node);
				if (node instanceof Text) {
    				var text = evaluate(node.data);
    				if (text) {
    					targetParent.appendChild(document.createTextNode(text));
    				}
    			}
				else if (node instanceof Element) {
    				var tagName = node.tagName;
    		    	var indexOfColon = tagName.indexOf(":");
    		    	if (indexOfColon<0) {
	    				var element = document.createElement(node.tagName);
	    				var attributes = node.attributes;
	    				for (var j=0; j<node.attributes.length; ++j) {
	    					var attribute = node.attributes[j];
	    					var value = attribute.value;
	    					if (!attribute.namespaceURI) {
		    					//console.info("processing ", attribute.name, "=", attribute.value);
		    					var newValue = evaluate(attribute.value);
		    					if (newValue !== undefined && newValue !== null) {
		    						element.setAttribute(attribute.name, newValue);
		    					}
	    					}
	    				}
	    				handleLater(node.childNodes, element, context);
	    				targetParent.appendChild(element);
    		    	}
    		    	else {
	    				var prefix = tagName.substring(0, indexOfColon);
    		    		tagName = tagName.substring(indexOfColon+1);
    		    		var nsUri = node.lookupNamespaceURI(prefix);
		    			switch (nsUri) {
		    			case Namespaces.Core:
    		    			handleCoreTag(tagName, node); break;
		    			case Namespaces.UI:
    		    			handleUiTag(tagName, node); break;
    		    		default:
    		    			//console.info(nsUri);
    		    			handleCustomTag(prefix, tagName, node);
    		    		}
    		    	} 
    			}
    			else if (node instanceof Comment) {
    				// TODO optional
    			}
    			/*
    			else if (node instanceof DocumentType || node instanceof DataNode || node instanceof XmlDeclaration) {
    				targetParent.appendChild(node.clone());
    			}
    			*/
			}
		}
		
		handle(xml.childNodes, result, context);
		
		while (tasks.length!=0) {
			tasks.shift()();
		}
		
		return result;
	}
	
	return {
		renderAndReturnNodes: function(context) {
			var container = process(context);
			var result = [];
			for (;;) {
				var child = container.childNodes[0];
				if (!child) { break; }
				container.removeChild(child);
				result.push(child);
			}
			return result;
		},
		render: function(context) {
			var container = process(context);
			/*
			var result = [];
			for (;;) {
				var child = container.childNodes[0];
				if (!child) { break; }
				container.removeChild(child);
				result.push(child);
			}
			return result;
			*/
			return container.innerHTML;
		} 
	}
}

var Facelets = function(resourceReader) 
{
	var cache = {};
	return { 
		get: function(name) {
			var result = cache[name];
			if (!result) {
				cache[name] = result = new Facelet(this, resourceReader(name));
			}
			return result;
		} 
	}
}

/*
var str = "hallo #{11} und nochmal hier #{x * 3 + y} yes";

var evaluate = Evaluator({x: 5, y: 1});
var result = evaluate(str);
console.info(result);
*/

var tmpl = '<div lang="#{lang}" xmlns:ui="http://java.sun.com/jsf/facelets" xmlns:c="http://java.sun.com/jsp/jstl/core" xmlns:j="http://beetmobile.com">'
	+ '<c:set var="yo" value="1"/>'
	+ '<c:if test="#{yo==1}"><p style="color: #{color}">Paragraph<br/>New Line</p></c:if>'
	+ '<c:if test="#{yo==2}"><span style="color: #{color}">Span</span></c:if>'
	+ '</div>';

var xml = parseXml(tmpl);
	
var template = Compiler.compile(tmpl);
var compiled = Template(xml).render({color: 'green', lang: 'de'});

for (var i=0; i<compiled.length; ++i) {
	console.info(compiled[i].outerHTML);
}

//document.body.appendChild(compiled);




