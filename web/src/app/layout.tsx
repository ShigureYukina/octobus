import "./globals.css";
import { ThemeProvider } from "@/provider/theme";
import { Toaster } from "@/components/ui/sonner"
import { LocaleProvider } from "@/provider/locale";
import QueryProvider from "@/provider/query";
import { ServiceWorkerRegister } from "@/components/sw-register";
import { TooltipProvider } from "@/components/animate-ui/components/animate/tooltip";



export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html suppressHydrationWarning>
      <head>
        <meta name="theme-color" content="#eae9e3" />
        <meta name="application-name" content="Octopus" />
        <meta name="apple-mobile-web-app-capable" content="yes" />
        <meta name="apple-mobile-web-app-status-bar-style" content="black" />
        <meta name="apple-mobile-web-app-title" content="Octopus" />
        <meta name="mobile-web-app-capable" content="yes" />
        <meta name="mobile-web-app-status-bar-style" content="black" />
        <meta name="mobile-web-app-title" content="Octopus" />
        <link rel="manifest" href="./manifest.json" />
        <link rel="icon" href="./favicon.ico" sizes="any" />
        <link rel="apple-touch-icon" href="./apple-icon.png" />
        <title>Octobus</title>
        <script
          dangerouslySetInnerHTML={{
            __html: `(function(){var r=document.documentElement,s=r.style,d=false;try{var t=localStorage.getItem("theme");if(t==="dark"||(!t&&window.matchMedia("(prefers-color-scheme:dark)").matches)){d=true;r.classList.add("dark")}}catch(e){}var c={};try{var raw=localStorage.getItem("octopus-settings");if(raw){var p=JSON.parse(raw);c=d?(p.state||{}).darkThemeColors||{}:(p.state||{}).lightThemeColors||{}}}catch(e){}var D=d?["0.18","0.92","0.22","0.92","0.22","0.92","0.70","0.15","0.28","0.88","0.25","0.68","0.70","0.15","0.32","0.32","0.70","0.15","0.15","0.70","0.70","0.15","0.32","0.70"]:["0.97","0.25","0.99","0.25","0.985","0.25","0.45","0.98","0.94","0.35","0.95","0.50","0.50","0.98","0.90","0.90","0.45","0.99","0.25","0.60","0.97","0.98","0.90","0.45"];var K="--background --foreground --card --card-foreground --popover --popover-foreground --primary --primary-foreground --secondary --secondary-foreground --muted --muted-foreground --accent --accent-foreground --border --input --ring --sidebar --sidebar-foreground --sidebar-primary --sidebar-primary-foreground --sidebar-accent --sidebar-accent-foreground --sidebar-border --sidebar-ring".split(" ");for(var i=0;i<K.length;i++){s.setProperty(K[i],"oklch("+D[i]+" 0 0)")}var M={background:0,foreground:1,card:2,cardForeground:3,popover:4,popoverForeground:5,primary:6,primaryForeground:7,secondary:8,secondaryForeground:9,muted:10,mutedForeground:11,accent:12,accentForeground:13,border:14,input:15,ring:16,sidebar:17,sidebarForeground:18,sidebarPrimary:19,sidebarPrimaryForeground:20,sidebarAccent:21,sidebarAccentForeground:22,sidebarBorder:23,sidebarRing:24};for(var k in M){if(c[k])s.setProperty(K[M[k]],c[k])}if(c.chartColors)for(var j=0;j<c.chartColors.length;j++)s.setProperty("--chart-"+(j+1),c.chartColors[j])})()`,
          }}
        />
        {/* Logo 加载动画已禁用 */}
        {/* <style
          dangerouslySetInnerHTML={{
            __html: `#initial-loader{position:fixed;inset:0;z-index:9999;display:flex;align-items:center;justify-content:center;background:var(--background);color:var(--primary);transition:opacity .2s}#initial-loader.octo-hide{opacity:0;pointer-events:none}#initial-loader svg{width:120px;height:120px}#initial-loader .octo-group{animation:octoFade 2s ease-in-out infinite}#initial-loader path{fill:none;stroke:currentColor;stroke-width:6;stroke-linecap:round;stroke-dasharray:1;stroke-dashoffset:1;opacity:0;animation:octoDraw 2s ease-in-out infinite both}#initial-loader path:nth-child(1){animation-delay:0s}#initial-loader path:nth-child(2){animation-delay:.15s}#initial-loader path:nth-child(3){animation-delay:.3s}#initial-loader path:nth-child(4){animation-delay:.45s}#initial-loader path:nth-child(5){animation-delay:.6s}@keyframes octoDraw{0%{stroke-dashoffset:1;opacity:0}5%{opacity:1}40%,100%{stroke-dashoffset:0;opacity:1}}@keyframes octoFade{0%,70%{opacity:1}100%{opacity:0}}@media(prefers-reduced-motion:reduce){#initial-loader .octo-group,#initial-loader path{animation:none!important;opacity:1!important;stroke-dashoffset:0!important}}`,
          }}
        /> */}
      </head>
      <body className="antialiased">
        {/* Logo 加载动画 HTML 已禁用 */}
        {/* <div id="initial-loader" role="status" aria-label="Loading">
          <svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
            <g className="octo-group">
              <path pathLength="1" d="M50 15 C70 15 85 30 85 50 C85 65 75 75 70 80 M50 15 C30 15 15 30 15 50 C15 65 25 75 30 80" />
              <path pathLength="1" d="M30 80 Q30 90 20 90" />
              <path pathLength="1" d="M43 77 Q43 90 38 90" />
              <path pathLength="1" d="M57 77 Q57 90 62 90" />
              <path pathLength="1" d="M70 80 Q70 90 80 90" />
            </g>
          </svg>
        </div> */}
        <ServiceWorkerRegister />
        <ThemeProvider attribute="class" defaultTheme="system" enableSystem>
          <QueryProvider>
            <LocaleProvider>
              <TooltipProvider>
                {children}
                <Toaster />
              </TooltipProvider>
            </LocaleProvider>
          </QueryProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
