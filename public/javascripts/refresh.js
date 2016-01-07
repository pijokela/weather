_charts = [];

function createChart(data, chartNumber) {
  var chart = _charts[chartNumber]
  if (chart != null) {
	  chart.destroy();
  }
  var ctx = document.getElementById("chart"+chartNumber).getContext("2d");
  _charts[chartNumber] = new Chart(ctx).Line(data);
}

Chart.defaults.global.animation = false;
Chart.defaults.global.scaleFontColor = "#AAA";
Chart.defaults.global.responsive = true;
Chart.defaults.global.tooltipTemplate = "<%if (label){%><%=label%>: <%}%><%= value %>";
Chart.defaults.global.scaleLabel = "<%=value%> C";
Chart.defaults.global.multiTooltipTemplate = "<%= datasetLabel %> - <%= value %>"
Chart.defaults.global.legendTemplate = "<ul class=\"<%=name.toLowerCase()%>-legend\"><% for (var i=0; i<datasets.length; i++){%><li><span style=\"background-color:<%=datasets[i].fillColor%>\"></span><%if(datasets[i].label){%><%=datasets[i].label%><%}%></li><%}%></ul>";

function loadData(timeframe) {
  $('#messages').html("");
  $.ajax({
    url: '/data?time=' + timeframe + '&type=temperature',
    success: function(data) {
      createChart(data, 1);
    },
    error: function(jqXhr, textStatus, errorThrown) {
      $('#messages').html("<div class='" + textStatus + "'>" + textStatus + ": " + errorThrown + "</div>");
    }
  });
  
  $.ajax({
    url: '/data?time=' + timeframe + '&type=pressure',
    success: function(data) {
      createChart(data, 2);
    },
    error: function(jqXhr, textStatus, errorThrown) {
      $('#messages').html("<div class='" + textStatus + "'>" + textStatus + ": " + errorThrown + "</div>");
    }
  });
}


function onHashChange() {
  var hash = location.hash;
  var time = hash.substring(1);
  loadData(time);
  $("div.links a").removeClass("selected");
  $("div.links a." + time).addClass("selected");
}

$( document ).ready(function(){
  var hash = location.hash;
  if (hash == null || hash == "" || hash == "#") {
	location.hash = "#previous24h";
  } else {
	// The page load does not trigger a "change"
	onHashChange();
  }
});
