function plot_data(r) {
    var miny = -1E-3;
    var maxy =  1E-3;

    var winx = $(window).width() * 0.8;
    var winy = $(window).height() / 3;
    
    var ecg_i = new Dygraph(
        document.getElementById("ecg_i"),
	r, {
	    title: 'I',
	    rollPeriod: 7,
	    animatedZooms: true,
	    xlabel: 't/sec',
	    width: winx,
	    height: winy,
	    valueRange: [ miny, maxy ],
	    visibility: [true, false, false, false, false, false, false],
	    zoomCallback: function(minX, maxX, yRanges) {
                ecg_ii.updateOptions({dateWindow: [minX,maxX]});
                ecg_iii.updateOptions({dateWindow: [minX,maxX]});
            },
	}
    );
    
    var ecg_ii = new Dygraph(
        document.getElementById("ecg_ii"),
	r, {
	    title: 'II',
	    rollPeriod: 7,
	    animatedZooms: true,
	    xlabel: 't/sec',
	    width: winx,
	    height: winy,
	    valueRange: [ miny, maxy ],
	    visibility: [false, true, false, false, false, false, false],
	    zoomCallback: function(minX, maxX, yRanges) {
                ecg_i.updateOptions({dateWindow: [minX,maxX]});
                ecg_iii.updateOptions({dateWindow: [minX,maxX]});
            },
	}
    );
    
    var ecg_iii = new Dygraph(
        document.getElementById("ecg_iii"),
	r, {
	    title: 'III',
	    rollPeriod: 7,
	    animatedZooms: true,
	    xlabel: 't/sec',
	    width: winx,
	    height: winy,
	    valueRange: [ miny, maxy ],
	    visibility: [false, false, true, false, false, false, false],
	    zoomCallback: function(minX, maxX, yRanges) {
                ecg_i.updateOptions({dateWindow: [minX,maxX]});
                ecg_ii.updateOptions({dateWindow: [minX,maxX]});
            },
	}
    );
    
    var reset = function() {
        var rng = ecg_iii.xAxisExtremes() 
        ecg_i.updateOptions({dateWindow: rng});
        ecg_ii.updateOptions({dateWindow: rng});        
        ecg_iii.updateOptions({dateWindow: rng});        
    };
    
    var pan = function(dir) {
        var w = ecg_iii.xAxisRange();
        var scale = w[1] - w[0];
        var amount = scale * 0.25 * dir;
        var rng = [ w[0] + amount, w[1] + amount ];
        ecg_i.updateOptions({dateWindow: rng});
        ecg_ii.updateOptions({dateWindow: rng});        
        ecg_iii.updateOptions({dateWindow: rng});        
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
