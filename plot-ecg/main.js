function plot_data(r) {
    var miny = -1.5E-3;
    var maxy =  1.5E-3;

    var winx = $(window).width() * 0.8;
    var winy = $(window).height() / 4;

    var axes_opt = {
              y: {
                valueFormatter: function(y) {
                  y = y * 1000;
                  return y.toPrecision(1);
                },
                axisLabelFormatter: function(y) {
                  y = y * 1000;
                  return y.toPrecision(1);
                },
                axisLabelWidth: 50,
                ticker: function(min, max, pixels, opts, dygraph, vals) {
                  return [{v:1E-3, label:"1"}, {v:-1E-3, label:"-1"}, {v:0, label:"0"}];
                },
              }
    };

    var ecg_i = new Dygraph(
        document.getElementById("ecg_i"),
	r, {
	    ylabel: 'I / mV',
            axes: axes_opt,
	    animatedZooms: true,
	    xlabel: 't/sec',
	    width: winx,
	    height: winy,
	    valueRange: [ miny, maxy ],
	    visibility: [true, false, false, false, false, false, false, false, false, false],
	    zoomCallback: function(minX, maxX, yRanges) {
                ecg_ii.updateOptions({dateWindow: [minX,maxX]});
                ecg_iii.updateOptions({dateWindow: [minX,maxX]});
                ecg_avr.updateOptions({dateWindow: [minX,maxX]});
                ecg_avl.updateOptions({dateWindow: [minX,maxX]});
                ecg_avf.updateOptions({dateWindow: [minX,maxX]});
                ecg_hr.updateOptions({dateWindow: [minX,maxX]});
            },
	}
    );

    var ecg_ii = new Dygraph(
        document.getElementById("ecg_ii"),
	r, {
	    ylabel: 'II / mV',
            axes: axes_opt,
	    animatedZooms: true,
	    xlabel: 't/sec',
	    width: winx,
	    height: winy,
	    valueRange: [ miny, maxy ],
	    visibility: [false, true, false, false, false, false, false, false, false, false],
	    zoomCallback: function(minX, maxX, yRanges) {
                ecg_i.updateOptions({dateWindow: [minX,maxX]});
                ecg_iii.updateOptions({dateWindow: [minX,maxX]});
                ecg_avr.updateOptions({dateWindow: [minX,maxX]});
                ecg_avl.updateOptions({dateWindow: [minX,maxX]});
                ecg_avf.updateOptions({dateWindow: [minX,maxX]});
                ecg_hr.updateOptions({dateWindow: [minX,maxX]});
            },
	}
    );

    var ecg_iii = new Dygraph(
        document.getElementById("ecg_iii"),
	r, {
	    ylabel: 'III / mV',
            axes: axes_opt,
	    animatedZooms: true,
	    xlabel: 't/sec',
	    width: winx,
	    height: winy,
	    valueRange: [ miny, maxy ],
	    visibility: [false, false, true, false, false, false, false, false, false, false],
	    zoomCallback: function(minX, maxX, yRanges) {
                ecg_i.updateOptions({dateWindow: [minX,maxX]});
                ecg_ii.updateOptions({dateWindow: [minX,maxX]});
                ecg_avr.updateOptions({dateWindow: [minX,maxX]});
                ecg_avl.updateOptions({dateWindow: [minX,maxX]});
                ecg_avf.updateOptions({dateWindow: [minX,maxX]});
                ecg_hr.updateOptions({dateWindow: [minX,maxX]});
            },
	}
    );

    var ecg_avr = new Dygraph(
        document.getElementById("ecg_avr"),
	r, {
	    ylabel: 'aVR / mV',
            axes: axes_opt,
	    animatedZooms: true,
	    xlabel: 't/sec',
	    width: winx,
	    height: winy,
	    valueRange: [ miny, maxy ],
	    visibility: [false, false, false, true, false, false, false, false, false, false],
	    zoomCallback: function(minX, maxX, yRanges) {
                ecg_i.updateOptions({dateWindow: [minX,maxX]});
                ecg_ii.updateOptions({dateWindow: [minX,maxX]});
                ecg_iii.updateOptions({dateWindow: [minX,maxX]});
                ecg_avl.updateOptions({dateWindow: [minX,maxX]});
                ecg_avf.updateOptions({dateWindow: [minX,maxX]});
                ecg_hr.updateOptions({dateWindow: [minX,maxX]});
            },
	}
    );

    var ecg_avl = new Dygraph(
        document.getElementById("ecg_avl"),
	r, {
	    ylabel: 'aVL / mV',
            axes: axes_opt,
	    animatedZooms: true,
	    xlabel: 't/sec',
	    width: winx,
	    height: winy,
	    valueRange: [ miny, maxy ],
	    visibility: [false, false, false, false, true, false, false, false, false, false],
	    zoomCallback: function(minX, maxX, yRanges) {
                ecg_i.updateOptions({dateWindow: [minX,maxX]});
                ecg_ii.updateOptions({dateWindow: [minX,maxX]});
                ecg_iii.updateOptions({dateWindow: [minX,maxX]});
                ecg_avr.updateOptions({dateWindow: [minX,maxX]});
                ecg_avf.updateOptions({dateWindow: [minX,maxX]});
                ecg_hr.updateOptions({dateWindow: [minX,maxX]});
            },
	}
    );

    var ecg_avf = new Dygraph(
        document.getElementById("ecg_avf"),
	r, {
	    ylabel: 'aVF / mV',
            axes: axes_opt,
	    animatedZooms: true,
	    xlabel: 't/sec',
	    width: winx,
	    height: winy,
	    valueRange: [ miny, maxy ],
	    visibility: [false, false, false, false, false, true, false, false, false, false],
	    zoomCallback: function(minX, maxX, yRanges) {
                ecg_i.updateOptions({dateWindow: [minX,maxX]});
                ecg_ii.updateOptions({dateWindow: [minX,maxX]});
                ecg_iii.updateOptions({dateWindow: [minX,maxX]});
                ecg_avl.updateOptions({dateWindow: [minX,maxX]});
                ecg_avr.updateOptions({dateWindow: [minX,maxX]});
                ecg_hr.updateOptions({dateWindow: [minX,maxX]});
            },
	}
    );

    var ecg_hr = new Dygraph(
        document.getElementById("ecg_hr"),
	r, {
	    ylabel: 'HR / bpm',
            axes: { y: {axisLabelWidth: 50} },
	    animatedZooms: true,
	    xlabel: 't/sec',
            drawPoints: true,
	    width: winx,
	    height: winy,
	    valueRange: [ 0, 200 ],
	    visibility: [false, false, false, false, false, false, true, false, false, false],
	    zoomCallback: function(minX, maxX, yRanges) {
                ecg_i.updateOptions({dateWindow: [minX,maxX]});
                ecg_ii.updateOptions({dateWindow: [minX,maxX]});
                ecg_iii.updateOptions({dateWindow: [minX,maxX]});
                ecg_avl.updateOptions({dateWindow: [minX,maxX]});
                ecg_avr.updateOptions({dateWindow: [minX,maxX]});
                ecg_avf.updateOptions({dateWindow: [minX,maxX]});
            },
	}
    );

    var reset = function() {
        var rng = ecg_iii.xAxisExtremes() 
        ecg_i.updateOptions({dateWindow: rng});
        ecg_ii.updateOptions({dateWindow: rng});
        ecg_iii.updateOptions({dateWindow: rng});
        ecg_avr.updateOptions({dateWindow: rng});
        ecg_avl.updateOptions({dateWindow: rng});
        ecg_avf.updateOptions({dateWindow: rng});
        ecg_hr.updateOptions({dateWindow: rng});
    };

    var pan = function(dir) {
        var w = ecg_iii.xAxisRange();
        var scale = w[1] - w[0];
        var amount = scale * 0.25 * dir;
        var rng = [ w[0] + amount, w[1] + amount ];
        ecg_i.updateOptions({dateWindow: rng});
        ecg_ii.updateOptions({dateWindow: rng});
        ecg_iii.updateOptions({dateWindow: rng});
        ecg_avr.updateOptions({dateWindow: rng});
        ecg_avl.updateOptions({dateWindow: rng});
        ecg_avf.updateOptions({dateWindow: rng});
        ecg_hr.updateOptions({dateWindow: rng});
    };

    document.getElementById('full').onclick = function() { reset(); };
    document.getElementById('left').onclick = function() { pan(-1); };
    document.getElementById('right').onclick = function() { pan(+1); };
}

function read_file_contents(fileobj) {
    if (fileobj) {
	var reader = new FileReader();
	reader.readAsText(fileobj, "UTF-8");
	reader.onload = function (evt) {
            document.getElementById("filename").innerHTML = fileobj.name;
	    plot_data(evt.target.result);
	}
	reader.onerror = function (evt) {
	    document.getElementById("message").innerHTML = "error reading file";
	}
    }
}

function upload_file(e) {
    e.preventDefault();
    fileobj = e.dataTransfer.files[0];
    read_file_contents(fileobj)
}

function file_explorer() {
    document.getElementById('selectfile').click();
    document.getElementById('selectfile').onchange = function() {
        fileobj = document.getElementById('selectfile').files[0];
	read_file_contents(fileobj)
    };
}
