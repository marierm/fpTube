FPTube {
	var <seen, <netAddr, <id, <dmx, <universe, <radio, <>lastSeen;

	* new { arg netAddr, id=1, dmx=1, universe=1, radio="", seen=false;
		^super.new.init(netAddr, id, dmx, universe, radio, seen);
	}

	init { arg na, identity, dmxOffset, uni, radioID, seenOnNetwork;
		this.netAddr_(na);
		this.id_(identity);
		this.dmx_(dmxOffset);
		this.universe_(uni);
		this.radio_(radioID);
		this.seen_(seenOnNetwork);
	}

	netAddr_ { arg netAddress;
		(netAddress.class != NetAddr).if({
			("Argument must be a NetAddr.").error;
		}, {
			netAddr = netAddress;
			this.changed(\netAddr);
		});
	}

	id_ { arg identity;
		(identity.class != Integer).if({
			("ID must be an integer.").error;
		}, {
			id = identity;
			netAddr.sendMsg("/tube/settubeid", id);
			this.changed(\id);
		});
	}

	dmx_ { arg dmxAddress=1;
		((dmxAddress > 512) or: (dmxAddress < 1)).if({
			("DMX channel must be an Integer between 1 and 512.").error;
		}, {
			dmx = dmxAddress;
			netAddr.sendMsg("/tube/setdmx", dmxAddress);
			this.changed(\dmx);
		});
	}

	universe_ { arg uni;
		(uni.class != Integer).if({
			("Universe should be an integer. It will default to 0. " ++ uni).warn;
			uni = 0;
		});
		universe = uni;
		netAddr.sendMsg("/tube/setuniverse", universe);
		this.changed(\universe);
	}

	radio_ { arg radioID;
		// (radioID.class != Char).if({
		// 	("Radio should be a capital character.  It is " ++ radioID).warn;
		// });
		radio = radioID;
		netAddr.sendMsg("/tube/setradio", radio);
		this.changed(\radio);
	}
	

	// on {
	// 	netAddr.sendMsg("/tube/setall/rgbw", 255, 255, 255, 255);
	// }

	// off {
	// 	netAddr.sendMsg("/tube/setall/rgbw", 0, 0, 0, 0);
	// }

	setAllRGBW { arg r=0, g=0, b=0, w=0;
		netAddr.sendMsg("/tube/setall/rgbw", r, g, b, w);
	}

	pause {
		netAddr.sendMsg("/tube/pause");
	}

	resume {
		netAddr.sendMsg("/tube/resume");
	}

	bang { arg time=1000;
		netAddr.sendMsg("/tube/bang", time);
	}

	rsync { arg folder;		
		netAddr.sendMsg( "/tube/rsync", NetAddr.localAddr.ip, folder );
	}

	ssh { arg cmdString="";
		var base, redir;
		base = "ssh -f -o \"StrictHostKeyChecking no\" pi@" ++ netAddr.ip ++ " nohup ";
		redir = " </dev/null >/dev/null 2>&1 &";
		(base ++ cmdString ++ redir).unixCmd;
	}

	quit {
		netAddr.sendMsg( "/tube/quit" );
	}

	start {
		this.ssh("sudo /home/pi/fpTube/fpTubeC/fpTube");
	}

	restart {
		this.quit;
		this.start;
	}

	reboot {
		fork {
			this.quit;
			0.5.wait;
			this.ssh("sudo reboot");
		};
	}

	gitPull {
		var base, cmdString;
		base = "ssh -o \"StrictHostKeyChecking no\" pi@" ++ netAddr.ip;
		cmdString = " \"cd /home/pi/fpTube; git pull\"";
		(base ++ cmdString).unixCmd;
	}

	sendMsg { arg ... args;
		netAddr.sendMsg(* args );
	}

	== { arg that;
		^this.compareObject(that, #[\netAddr])
	}

	check { |timeOut=2.0|
		((thisThread.seconds - lastSeen) > timeOut).if({
			this.seen_(false);
		},{
			this.seen_(true);
		});
		^seen;
	}

	seen_ { arg bool;
		(seen != bool).if({
			seen = bool;
			seen.if({
				this.changed(\tubeIsBack);
			},{
				this.changed(\tubeRemoved);
			});
		});
	}

	openEditor {
		FPTubeEditor(this).gui;
	}
	
	guiClass { ^FPTubeGui }
}


TubeManager {
	var <tubes, <oscFunc, <checkTask;
	var tubeOutPort=55999, tubeInPort=55801;
	* new {
		^super.new.init();
	}

	init { arg ips;
		tubes = Array.newClear(256);
		// ips = tubes.collect({ |tube| tube.netAddr.ip });
		oscFunc = OSCFunc({ |msg, time, addr, port|
			var ipLastByte = addr.ip.split($.)[3].asInteger;
			this.addTube(
				addr, msg[1], msg[2], msg[3], msg[4], true
			);
			tubes[ipLastByte].lastSeen_(time);
			tubes[ipLastByte].seen_(true);
		}, "/tube/dmx", nil, tubeOutPort); // change message to /tube/conf (or something better)
		
		Tdef(\checkTubes, {
			loop {
				2.0.wait;
				tubes.select(_.notNil).do({ |tube|
					tube.check;
				});
			}
		});
		Tdef(\checkTubes).play;
	}

	addTube { arg addr, id, dmx, universe, radio, seen=true;
		var ipLastByte = addr.ip.split($.)[3].asInteger;
		// does nothing if there already is a tube there.
		tubes[ipLastByte].isNil.if({ 
			tubes.put(
				ipLastByte,
				FPTube(addr, id, dmx, universe, radio, seen)
			);
			this.changed(\tubeAdded, tubes[ipLastByte]);
		});
	}

	trace { arg bool=true;
		bool.if({
			OSCdef(\fpTubeTraceOSC, { |... all|
				all.postln;
			}, "/tube/dmx", nil, tubeOutPort); // change message to /tube/conf (or something better)
		}, {
			OSCdef(\fpTubeTraceOSC, {} ); // change message to /tube/conf (or something better)
		});
	}

	guiClass { ^TubeManagerGui }
	
}


TubeManagerGui : ObjectGui { 
	var <tree, buttons;

	update { |model, what ... args|
		what.switch(
			\tubeAdded, {
				//args[0] is a new FPTube.
				{
					FPTubeView(tree,args[0]);
				}.defer;
			}
		);
	}

	gui {arg layout,bounds ... args;
		var window, image;
		image = Image.open("/home/marierm/.local/share/SuperCollider/Extensions/fpTube/compagnia-finzi-pasca-227x90.png");
		window = Window("Tube Manager", Rect.aboutPoint(Window.screenBounds.center, 300, 300)).layout_(
			VLayout(
				StaticText(nil, 300@50),
				tree = TreeView().columns_(
					["on", "IP", "ID", "DMX address", "Universe", "Radio", "Bang"]
				).resize_(5);
			)
		);
		model.tubes.select(_.notNil).do{|i,j|
			FPTubeView(tree,i,j);
		};

		// buttons = tree.addItem(["",""]).setView(
		// 	0,
		// 	Button().states_([["Add"]]).action_({
		// 		model.add()
		// 	})
		// ).setView(
		// 	1,
		// 	Button().states_([["Randomize"]]).action_({
		// 		model.randomizeParameters;
		// 	})
		// );
		tree.mouseDownAction_({ |v,x,y,mod,num,count|
			(count==2).if({
				var ipLastByte;
				ipLastByte = v.currentItem.strings[1].split($.)[3].asInteger;
				model.tubes[ipLastByte].openEditor;
			});
		});
		// tree.setProperty(\windowTitle, "Tube Manager");		
		window.onClose_({
			model.removeDependant(this);
			Tdef(\checkTubes).stop;
		});
		tree.canSort_(true);
		window.front;
	}

	background_ { |color|
		tree.background_(color ? Color.grey);
	}

}

FPTubeView {
	var tree, <fpTube, <treeItem;
	var on, ip, id, dmx, universe, radio, bang;

	*new { |parent, tube|
		^super.new.init(parent, tube)
	}

	init { |parent, aTube, id|
		tree = parent;
		fpTube = aTube;
		fpTube.addDependant(this);
		treeItem = tree.addItem([""]);
		fpTube.seen.if({
			treeItem.setString(0, "*")
		},{
			treeItem.setString(0, "")
		});
		treeItem.setString(1, fpTube.netAddr.ip);
		treeItem.setString(2, fpTube.id.asString.padLeft(3, "0"));
		treeItem.setString(3, fpTube.dmx.asString.padLeft(3, "0"));
		treeItem.setString(4, fpTube.universe.asString.padLeft(3, "0"));
		treeItem.setString(5, fpTube.radio.asString);
		treeItem.setView(6, Button().states_(
			[["BANG!", Color.black, Color.white]]
		).action_({|butt|
			fpTube.bang(1000);
		}));
	}

	update { |tube, what ... args|
		what.switch(
			// \spec, {
			// 	slider.value_(parameter.value);
			// 	mapped.value_(parameter.mapped);
			// 	unmapped.value_(parameter.value);
			// },
			// \value, {
			// 	slider.value_(parameter.value);
			// 	mapped.value_(parameter.mapped);
			// 	unmapped.value_(parameter.value);
			// },
			// \name, {
			// 	name.string_(parameter.name);
			// },
			\tubeRemoved, {
				tube.postln;
				\tubeRemoved.postln;
				{
					treeItem.textColors_(Color.grey(0.6)!7);
					treeItem.setString(0, "");
				}.defer;
			},
			\tubeIsBack, {
				{
					treeItem.textColors_(Color.grey(0.0)!7);
					treeItem.setString(0, "*");
				}.defer;
			},
			\id, {
				{
					treeItem.setString(
						2,
						fpTube.id.asString.padLeft(3, "0")
					);
				}.defer;
			},
			\dmx, {
				{
					treeItem.setString(
						3,
						fpTube.dmx.asString.padLeft(3, "0")
					);
				}.defer;
			},
			\universe, {
				{
					treeItem.setString(
						4,
						fpTube.universe.asString.padLeft(3, "0")
					);
				}.defer;
			},
			\radio, {
				{
					treeItem.setString(
						5,
						fpTube.radio.asString
					);
				}.defer;
			}
			// \OSC, {
			// 	parameter.sendOSC.if({
			// 		oscButt.states_(
			// 			[[parameter.oscMess, Color.black, Color.white]]
			// 		)				
			// 	},{
			// 		oscButt.states_(
			// 			[["none",Color.grey(0.3), Color.grey(0.4)]]
			// 		)
			// 	});
			// }
		);
	}
}

FPTubeGui : ObjectGui { 
	gui {
		var tree;
		// layout.resize_(3);
		tree = TreeView().columns_(
			["on", "IP", "ID", "DMX address", "Universe", "Radio", "Bang"]
		).resize_(3);
		FPTubeView(tree, model, 0);
		tree.front;
	}
}

FPTubeEditor {
	var <fpTube;
	
	* new { arg fpTube;
		^super.new.init(fpTube);
	}

	init { arg tube;
		fpTube = tube;
	}
	
	guiClass { ^FPTubeEditorGui }
}

FPTubeEditorGui : ObjectGui {
	gui {
		var window, idBox, dmxBox, uniBox, radioBox;
		window = Window("Tube " ++ model.fpTube.netAddr.ip, Rect.aboutPoint(Window.screenBounds.center, 300, 40));
		window.layout = HLayout(
			VLayout(
				StaticText().string_("Id").align_(\center),
				NumberBox().value_(
					model.fpTube.id
				).action_({|v|
					model.fpTube.id_(v.value.asInteger)
				}).align_(\center)
			),
			VLayout(
				StaticText().string_("DMX\nAddress").align_(\center),
				PopUpMenu().items_(
					((0,15..495)+1).collect({ |i, j| ((j+1) + ":" + i).asString; });
				).value_(
					(model.fpTube.dmx-1) / 15
				).action_({ |menu|
					model.fpTube.dmx_((menu.value.asInteger * 15) + 1;);
				})
			),
			VLayout(
				StaticText().string_("Universe").align_(\center),
				NumberBox().value_(
					model.fpTube.universe;
				).action_({ |v|
					model.fpTube.universe_(v.value.asInteger);
				}).align_(\center)
			),
			VLayout(
				StaticText().string_("radio").align_(\center),
				TextField().value_(
					model.fpTube.radio;
				).action_({ |v|
					model.fpTube.radio_(v.value);
				}).align_(\center)
			),
		);
		window.front;
	}
}
