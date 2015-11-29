function createChart(data) {
  var ctx = document.getElementById("chart1").getContext("2d");
  var myNewChart = new Chart(ctx).Line(data);
}

Chart.defaults.global.responsive = true;

$( document ).ready(function(){
  $.ajax({
    url: '/data', 
    success: function(data) {
      $('#messages').html("");
      createChart(data);
    },
    error: function(jqXhr, textStatus, errorThrown) {
      $('#messages').html("<div class='" + textStatus + "'>" + textStatus + ": " + errorThrown + "</div>");
    }
  });
});