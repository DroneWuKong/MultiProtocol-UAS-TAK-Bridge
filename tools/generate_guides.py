"""Rebuild both public PDFs and Markdown from guide_content.py and verified screenshots.
Requires ReportLab and DejaVu Sans fonts. Images are committed software-test renders.
"""
from pathlib import Path
from xml.sax.saxutils import escape
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.colors import HexColor
from reportlab.lib.styles import ParagraphStyle
from reportlab.platypus import Paragraph, Table, TableStyle
from reportlab.lib.utils import ImageReader
from guide_content import MANUAL, QUICK, VERSION

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'docs/guide-assets'
FONT_ROOT = Path('/usr/share/fonts/truetype/dejavu')
for name, file in [('Prismo','DejaVuSans.ttf'),('PrismoBold','DejaVuSans-Bold.ttf')]:
    pdfmetrics.registerFont(TTFont(name, str(FONT_ROOT / file)))
pdfmetrics.registerFontFamily('Prismo',normal='Prismo',bold='PrismoBold')
NAVY=HexColor('#0D141B'); PANEL=HexColor('#17222E'); TEAL=HexColor('#63DFC6')
CREAM=HexColor('#F1E6D2'); PAPER=HexColor('#FBF9F4'); INK=HexColor('#182A34')
MUTED=HexColor('#526571'); LINE=HexColor('#D5DFDC'); AMBER=HexColor('#FFF0D7')
URL='https://github.com/DroneWuKong/MultiProtocol-UAS-TAK-Bridge'
W,H=612,792; M=44; WIDTH=W-2*M

def styles(size=9.6):
    return {
        'p':ParagraphStyle('p',fontName='Prismo',fontSize=size,leading=size*1.43,textColor=INK,spaceAfter=8),
        'h':ParagraphStyle('h',fontName='PrismoBold',fontSize=12,leading=16,textColor=INK,spaceBefore=8,spaceAfter=6),
        'small':ParagraphStyle('small',fontName='Prismo',fontSize=8,leading=11,textColor=MUTED),
        'cell':ParagraphStyle('cell',fontName='Prismo',fontSize=size-.3,leading=(size-.3)*1.38,textColor=INK),
    }

def paragraph(c,text,style,x,y,width,draw=True):
    p=Paragraph(text,style);_,h=p.wrap(width,720)
    if draw:p.drawOn(c,x,y-h)
    return y-h

def blocks(c,items,x,y,width,size,draw=True):
    st=styles(size)
    for item in items:
        kind=item[0]
        if kind in ('h','p'):
            style=st[kind]
            y-=style.spaceBefore
            y=paragraph(c,item[1],style,x,y,width,draw)-style.spaceAfter
        elif kind=='table':
            rows=[[Paragraph(escape(str(v)),st['cell']) for v in row] for row in item[1]]
            contents_table=item[1][0][0][:1].isdigit()
            widths=[55,width-55] if contents_table or item[1][0][0] == "Step" else [width*.33,width*.67]
            t=Table(rows,colWidths=widths,hAlign='LEFT')
            t.setStyle(TableStyle([
                ('BACKGROUND',(0,0),(-1,0),PAPER if contents_table else HexColor('#E6EDE8')),
                ('VALIGN',(0,0),(-1,-1),'TOP'),
                ('LINEBELOW',(0,0),(-1,-1),.4,LINE),
                ('LEFTPADDING',(0,0),(-1,-1),8),('RIGHTPADDING',(0,0),(-1,-1),8),
                ('TOPPADDING',(0,0),(-1,-1),6),('BOTTOMPADDING',(0,0),(-1,-1),6)
            ]))
            _,height=t.wrap(width,720)
            if draw:t.drawOn(c,x,y-height)
            y-=height+12
        elif kind=='note':
            inner=width-24
            h1=Paragraph(item[1],st['h']).wrap(inner,720)[1]
            h2=Paragraph(item[2],st['p']).wrap(inner,720)[1]
            height=h1+h2+30
            if draw:
                c.setFillColor(AMBER);c.roundRect(x,y-height,width,height,8,fill=1,stroke=0)
                c.setFillColor(HexColor('#BD8544'));c.rect(x,y-height+8,3,height-16,fill=1,stroke=0)
                ny=paragraph(c,item[1],st['h'],x+12,y-10,inner)-6
                paragraph(c,item[2],st['p'],x+12,ny,inner)
            y-=height+12
        else:raise ValueError(kind)
    return y

def footer(c,page,count,name):
    c.setStrokeColor(LINE);c.line(M,43,W-M,43)
    c.setFont('Prismo',7.5);c.setFillColor(MUTED)
    c.drawString(M,28,f'PRISMO / TAK BRIDGE  ·  {VERSION}  ·  {name}')
    c.drawRightString(W-M,28,f'{page:02d} / {count:02d}')
    c.setFont('Prismo',6.5);c.drawString(M,15,URL.removeprefix('https://'))
    c.linkURL(URL,(M,10,450,36),relative=0)

def screenshot(c,filename,x,y,width,caption):
    path=ASSETS/filename
    if not path.exists():raise FileNotFoundError(f'Verified screenshot required: {path}')
    im=ImageReader(str(path));iw,ih=im.getSize();height=width*ih/iw
    c.setFillColor(PANEL);c.roundRect(x-5,y-height-5,width+10,height+10,12,fill=1,stroke=0)
    c.drawImage(im,x,y-height,width,height,mask='auto')
    paragraph(c,caption,styles()['small'],x,y-height-18,width)

def build(pages,filename,name):
    out=ROOT/'docs'/filename
    c=canvas.Canvas(str(out),pagesize=(W,H),pageCompression=1,invariant=1)
    c.setTitle(f'TAK Bridge {name}');c.setAuthor('Prismo / DroneWuKong')
    c.setSubject(f'Public bridge operation and planning guide, version {VERSION}')
    for n,page in enumerate(pages,1):
        c.bookmarkPage(f'p{n}');c.addOutlineEntry(page['title'],f'p{n}',level=0)
        c.setFillColor(PAPER);c.rect(0,0,W,H,fill=1,stroke=0)
        if page.get('kind')=='cover':
            c.setFillColor(NAVY);c.rect(0,520,W,272,fill=1,stroke=0)
            c.setFillColor(TEAL);c.setFont('PrismoBold',11);c.drawString(M,735,'PRISMO / FIELD SYSTEMS')
            c.setFillColor(CREAM);c.setFont('PrismoBold',42);c.drawString(M,670,'TAK Bridge')
            c.setFont('Prismo',23);c.drawString(M,634,page['subtitle'])
            c.setFillColor(TEAL);c.rect(M,611,58,3,fill=1,stroke=0)
            style=ParagraphStyle('cover',fontName='Prismo',fontSize=12,leading=18,textColor=HexColor('#EDF4FA'))
            paragraph(c,page['intro'],style,M,592,465)
            c.setFont('Prismo',9);c.setFillColor(CREAM);c.drawString(M,535,f'Development build {VERSION}  /  October 2026')
            x,y,width=M,495,WIDTH
        else:
            c.setFillColor(NAVY);c.rect(0,675,W,117,fill=1,stroke=0)
            c.setFillColor(TEAL);c.setFont('PrismoBold',9);c.drawString(M,749,'PRISMO / TAK BRIDGE')
            c.setFillColor(CREAM);c.setFont('PrismoBold',21);c.drawString(M,710,page['title'])
            c.setFont('Prismo',9);c.setFillColor(TEAL)
            c.drawRightString(W-M,749,f'{name.upper()}  /  {n:02d}')
            x,y,width=M,651,WIDTH
            if page.get('image'):
                screenshot(c,page['image'],M,y,172,page['caption'])
                x=M+198;width=WIDTH-198
        # Keep the body readable; fail loudly instead of clipping a page.
        size=9.6
        while blocks(c,page['blocks'],x,y,width,size,False)<60 and size>8.8:
            size=round(size-.2,1)
        bottom=blocks(c,page['blocks'],x,y,width,size,False)
        if bottom<60:raise ValueError(f'{filename} page {n} overflows ({bottom:.1f}); edit copy/layout')
        blocks(c,page['blocks'],x,y,width,size)
        footer(c,n,len(pages),name)
        c.showPage()
        print(f'{filename} p{n}: body {size}pt, bottom {bottom:.0f}pt')
    c.save()
    return out

def markdown(pages,filename,title):
    lines=[f'# {title}',f'\nPrismo / TAK Bridge · Development build {VERSION}\n']
    for n,page in enumerate(pages,1):
        lines += [f'## {n:02d}. {page["title"]}','']
        if page.get('intro'):lines += [page['intro'],'']
        if page.get('image'):lines += [f'![{page["caption"]}](guide-assets/{page["image"]})','']
        for item in page['blocks']:
            if item[0]=='h':lines += [f'### {item[1]}','']
            elif item[0]=='p':lines += [item[1].replace('<b>','**').replace('</b>','**'),'']
            elif item[0]=='note':lines += [f'> **{item[1]}** — {item[2]}','']
            elif item[0]=='table':
                rows=item[1]
                if rows[0][0][:1].isdigit(): rows=[['Page','Section']]+rows
                lines += ['| '+' | '.join(rows[0])+' |','| --- | --- |']
                lines += ['| '+' | '.join(row)+' |' for row in rows[1:]];lines += ['']
    (ROOT/'docs'/filename).write_text('\n'.join(lines))

if __name__=='__main__':
    build(MANUAL,'TAK_Bridge_User_Manual.pdf','User Manual')
    build(QUICK,'TAK_Bridge_Quick_Start.pdf','Quick Start')
    markdown(MANUAL,'USER_MANUAL.md','TAK Bridge User Manual')
    markdown(QUICK,'QUICK_START.md','TAK Bridge Quick Start')
