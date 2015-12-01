function createChart(data) {
  var ctx = document.getElementById("chart1").getContext("2d");
  var myNewChart = new Chart(ctx).Line(data);
}

Chart.defaults.global.responsive = true;
Chart.defaults.global.tooltipTemplate = "foo<%if (label){%><%=label%>: <%}%><%= value %>";
Chart.defaults.global.scaleLabel = "<%=value%> C";
Chart.defaults.global.multiTooltipTemplate = "<%= datasetLabel %> - <%= value %>"
Chart.defaults.global.legendTemplate = "<ul class=\"<%=name.toLowerCase()%>-legend\"><% for (var i=0; i<datasets.length; i++){%><li><span style=\"background-color:<%=datasets[i].fillColor%>\"></span><%if(datasets[i].label){%><%=datasets[i].label%><%}%></li><%}%></ul>";


$( document ).ready(function(){
  $.ajax({
    url: '/data?grouping=hourly', 
    success: function(data) {
      $('#messages').html("");
      createChart(data);
    },
    error: function(jqXhr, textStatus, errorThrown) {
      $('#messages').html("<div class='" + textStatus + "'>" + textStatus + ": " + errorThrown + "</div>");
    }
  });
});