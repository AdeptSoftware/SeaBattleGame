package adeptsoftware.seabattlegame;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Build;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
// Дополнительные модули
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import java.util.ArrayList;

import java.util.Random;

class MainView extends View
{
    private int m_nCellSize = 30;                   // Размер ячейки
    private int m_nDistance = 10;                   // Расстояние между полей
    private Point m_nOffset = new Point();          // Смещение от краёв
    private Point m_szDisplay = new Point();        // Размер экрана
    private boolean m_bHorizontal = false;
    private Paint m_paint = new Paint();
    private Rect m_rc;
    private boolean m_bShortName = false;

    // Игровые данные
    private class Data
    {
        // Храним здесь куда уже ходили игроки
        boolean[][] click = new boolean[10][10];
        // Храним карту кораблей (0 - пусто, 1 - корабль, 2 - ближ.зона)
        int[][] map = new int[10][10];
        int nAlive = 20; //4*1+3*2+2*3+1*4
        Rect[] rcShips = new Rect[10];    //Корабли [0: 4х, 1-2: 3x, 3-5: 2x, 6-9: 1x]
        boolean[] bShipDestroyed = new boolean[10]; // Уничтожен ли корабль
        boolean[] bAreaShaded = new boolean[10]; // Закрашена ли область вокруг корабля
    }

    private int m_nMoveCount;
    private Data m_user;
    private Data m_enemy;
    private Context m_context;

    public MainView(Context context)
    {
        super(context);
        m_context = context;
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getSize(m_szDisplay);
        ReCalc();
    }

    private void ReCalc()
    {// Обычно ширина или высота экрана не более чем в 2 раза различаются друг от друга
        int nMax;
        int nMin;
        m_bShortName = false;
        if (m_szDisplay.x < m_szDisplay.y)
        {
            m_bHorizontal = false;
            nMax = m_szDisplay.y;
            nMin = m_szDisplay.x;
        }
        else
        {
            m_bHorizontal = true;
            nMax = m_szDisplay.x;
            nMin = m_szDisplay.y;
        }
        m_nCellSize = (nMax-(m_nDistance+51))/20;
        //Выравним
        nMax = ((nMax-((m_nCellSize*20)+m_nDistance))/2);
        nMin =  ((nMin-(m_nCellSize*10))/2)+1;

        if (m_bHorizontal)
        {
            m_nOffset = new Point(nMax, nMin);
            m_rc = new Rect(nMax, nMin-52, nMax+125, nMin-5);
        }
        else
        {
            m_nOffset = new Point(nMin, nMax-23);
            m_rc = new Rect(5, m_nOffset.y+1, nMin-5, m_nOffset.y+48);
        }
        if (m_rc.width() < 125)
        {
            m_bShortName = true;
            m_rc.right = m_rc.left + 45;
        }
    }

    private void DrawField(Canvas canvas, int x, int y, int nFieldSize)
    {
        m_paint.setStyle(Paint.Style.STROKE);
        m_paint.setColor(Color.BLACK);
        for (int i=0; i<11; i++)
        {
            int offset = m_nCellSize*i;
            // Горизонтальные линии
            canvas.drawLine(x+m_nOffset.x, y+offset+m_nOffset.y, x+m_nOffset.x+nFieldSize, y+offset+m_nOffset.y, m_paint);
            // Рисуем вертикальные линии
            canvas.drawLine(x+offset+m_nOffset.x, y+m_nOffset.y, x+offset+m_nOffset.x, y+m_nOffset.y+nFieldSize, m_paint);
        }
    }

    private boolean DrawCell(Canvas canvas, int x, int y, Data P1, Data P2, boolean bUserP1)
    {
        if (P1.click[x][y])
        {
            int clr;
            if (P2.map[x][y] == 1)      // Подбит/потоплен
                clr = Color.RED;
            else
                clr = Color.LTGRAY;     // Мимо
            m_paint.setColor(clr);
            canvas.drawRect(GetRect(x, y, !bUserP1), m_paint);
            return true;
        }
        return false;
    }

    public void onDraw(Canvas canvas)
    {
        int nFieldSize = m_nCellSize*10;
        DrawField(canvas, 0, 0, nFieldSize);
        if (m_szDisplay.x > m_szDisplay.y)
            DrawField(canvas, nFieldSize+m_nDistance, 0, nFieldSize);
        else
            DrawField(canvas, 0, nFieldSize+m_nDistance, nFieldSize);

        m_paint.setColor(Color.rgb(255, 128, 0));
        m_paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(m_rc, m_paint);
        m_paint.setColor(Color.BLACK);
        m_paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(m_rc, m_paint);
        m_paint.setTextSize(20);
        String str = "Новая игра";
        if (m_bShortName)
            str = "НИ";
        canvas.drawText(str, m_rc.left+10, m_rc.top+30, m_paint);

        //закрасим необходимые области
        m_paint.setStyle(Paint.Style.FILL);
        for (int x=0; x<10; x++)
        {
            for (int y=0; y<10; y++)
            {// Заполним сначало вражеское поле, а потом своё
                DrawCell(canvas, x, y, m_user, m_enemy, true);
                if (!DrawCell(canvas, x, y, m_enemy, m_user, false))
                {
                    if (m_user.map[x][y] == 1 && !m_enemy.click[x][y])
                    {
                        m_paint.setColor(Color.BLACK);
                        canvas.drawRect(GetRect(x, y, true), m_paint);
                    }
                }
            }
        }
    }

    private Rect GetRect(int x, int y, boolean bUserField)
    {
        int nOffset = 0;
        if (!bUserField)
            nOffset = (m_nCellSize*10)+m_nDistance;
        int c_x, c_y;
        if (m_szDisplay.x > m_szDisplay.y)
        {
            c_x = (m_nCellSize*x)+m_nOffset.x+nOffset;
            c_y = (m_nCellSize*y)+m_nOffset.y;
        }
        else
        {
            c_x = (m_nCellSize*x)+m_nOffset.x;
            c_y = (m_nCellSize*y)+m_nOffset.y+nOffset;
        }
        return new Rect(c_x+3, c_y+3, c_x+m_nCellSize-2, c_y+m_nCellSize-2);
    }

    public boolean CheckPos(int x, int y)
    {
        Point ptPos = new Point(-1, -1);
        if ((m_rc.top <= y && y <= m_rc.bottom) && (m_rc.left <= x && x <= m_rc.right))
            NewGame();
        else
        {
            if (m_bHorizontal)
                ptPos = ConvertPos(x, y, m_nOffset.x, m_nOffset.y, m_szDisplay.x);
            else
            {
                ptPos = ConvertPos(y, x, m_nOffset.y, m_nOffset.x, m_szDisplay.y);
                ptPos = new Point(ptPos.y, ptPos.x);
            }

        }
        return OnUserAction(ptPos);
    }

    private Point ConvertPos(int a, int b, int nOffsetA, int nOffsetB, int nLengthA)
    {
        int nFieldSize = m_nCellSize*10;
        if (b >= nOffsetB && b <= nFieldSize + nOffsetB)
        {
            if (a >= nFieldSize + nOffsetA + m_nDistance && a <= nLengthA - nOffsetA)
            {
                a -= nFieldSize + m_nDistance;
                int k = (b - nOffsetB) % m_nCellSize;
                Point pos = new Point();
                pos.y = (b - k - nOffsetB) / m_nCellSize;
                k = (a - nOffsetA) % m_nCellSize;
                pos.x = (a - k - nOffsetA) / m_nCellSize;
                return pos;
            }
        }
        return new Point(-1, -1);
    }

    @Override
    public boolean performClick()
    {
        return super.performClick();
    }

    private boolean CheckEndGame()
    {// Вернет true - если игра завершена
        if (m_enemy.nAlive <= 0 || m_user.nAlive <= 0 || m_nMoveCount >= 200)
        {// Выведем сообщение, кто победил и начнем новую
            String str = "";
            if (m_enemy.nAlive <= 0)
                str = "Адмирал, враг уничтожен! Ура!";
            if (m_user.nAlive <= 0)
                str = "Адмирал, мне очень жаль, но мы потеряли весь флот!";
            if (m_nMoveCount >= 200)
                str += "\nНет времени объяснять...";
            AlertDialog.Builder builder = new AlertDialog.Builder(m_context);
            builder.setTitle("Доклад");
            builder.setMessage(str);
            builder.setCancelable(false);
            builder.setNegativeButton("Ок", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    dialog.cancel();
                }
            });
            AlertDialog alert = builder.create();
            alert.show();

            NewGame();
            return true;
        }
        return false;
    }

    private boolean OnUserAction(Point ptPos)
    {
        if ((ptPos.x >= 0 && ptPos.x < 10) && (ptPos.y >= 0 && ptPos.y < 10))
        {
            if (!m_user.click[ptPos.x][ptPos.y])
            {
                IncreaseMoveCount(m_user, ptPos.x, ptPos.y);
                if (m_enemy.map[ptPos.x][ptPos.y] != 1)
                    OnEnemyAction();
                else
                {
                    DecreaseAliveCount(m_user, m_enemy, ptPos.x, ptPos.y);
                    CheckEndGame();
                }
                return true;
            }
        }
        return false;
    }

    private ArrayList<Point> GetPossibleMoves()
    {
        ArrayList<Point> moves = new ArrayList<>();
        for (int x=0; x<10; x++)
        {
            for (int y=0; y<10; y++)
            {
                if (m_enemy.click[x][y] && m_user.map[x][y] == 1)
                {
                    boolean bRight = (x+1 < 10 && m_enemy.click[x+1][y] && m_user.map[x+1][y] == 1);
                    boolean bLeft = (x-1 >= 0 && m_enemy.click[x-1][y] && m_user.map[x-1][y] == 1);
                    boolean bTop = (y-1 >= 0 && m_enemy.click[x][y-1] && m_user.map[x][y-1] == 1);
                    boolean bBottom = (y+1 < 10 && m_enemy.click[x][y+1] && m_user.map[x][y+1] == 1);
                    if (bLeft || bRight)
                    {// Проверим по горизонтали
                        if (bLeft)
                        {// Логика такая если левее есть не нажатая кнопка, то добавляем
                            if(x-2 >= 0 && !m_enemy.click[x-2][y])
                                moves.add(new Point(x-2, y));
                            else
                            {// Если нет, то может быть она есть правее?
                                if (x+1 < 10 && !m_enemy.click[x+1][y])
                                    moves.add(new Point(x+1, y));
                            }
                        }

                        if (bRight)
                        {
                            if (x+2 < 10 && !m_enemy.click[x+2][y])
                                moves.add(new Point(x+2, y));
                            else
                            {
                                if (x-1 >= 0 && !m_enemy.click[x-1][y])
                                    moves.add(new Point(x-1, y));
                            }
                        }
                    }
                    else
                    {
                        if (bTop || bBottom)
                        {//Проверим по вертикали
                            if (bTop)
                            {
                                if (y-2 >= 0 && !m_enemy.click[x][y-2])
                                    moves.add(new Point(x, y-2));
                                else
                                {
                                    if (y+1 < 10 && !m_enemy.click[x][y+1])
                                        moves.add(new Point(x, y+1));
                                }
                            }
                            if (bBottom)
                            {
                                if (y+2 < 10 && !m_enemy.click[x][y+2])
                                    moves.add(new Point(x, y+2));
                                else
                                {
                                    if (y-1 >= 0 && !m_enemy.click[x][y-1])
                                        moves.add(new Point(x, y-1));
                                }
                            }
                        }
                        else
                        {//Добавим все доступные варианты
                            if (x+1 < 10 && !m_enemy.click[x+1][y])
                                moves.add(new Point(x+1, y));
                            if (x-1 >= 0 && !m_enemy.click[x-1][y])
                                moves.add(new Point(x-1, y));
                            if (y+1 < 10 && !m_enemy.click[x][y+1])
                                moves.add(new Point(x, y+1));
                            if (y-1 >= 0 && !m_enemy.click[x][y-1])
                                moves.add(new Point(x, y-1));
                        }
                    }
                }
            }
        }
        return moves;
    }

    private void IncreaseMoveCount(Data data, int x, int y)
    {
        data.click[x][y] = true;
        m_nMoveCount++;
    }

    private void DecreaseAliveCount(Data P1, Data P2, int x, int y)
    {//P1 - кто ходил; P2 - в кого попали
        P2.nAlive--;
        // (-1,-1), (-1,1), (1,-1), (1,1) - никогда не будет там ответа.
        // Человеку очевидно, а устройству нет. Упрощаем ему работу.
        if (x-1 >= 0 && y+1 < 10)
            P1.click[x-1][y+1] = true;
        if (x-1 >= 0 && y-1 >= 0)
            P1.click[x-1][y-1] = true;
        if (x+1 < 10 && y+1 < 10)
            P1.click[x+1][y+1] = true;
        if (x+1 < 10 && y-1 >= 0)
            P1.click[x+1][y-1] = true;
        int nCurrentShip = 0;
        for (int i=0; i<10; i++)
        {
            //Текущая позиция
            if ((x >= P2.rcShips[i].left && x <= P2.rcShips[i].right) &&
                (y >= P2.rcShips[i].top && y <= P2.rcShips[i].bottom))
                nCurrentShip = i;
            // Обновим состояние кораблей (убит/неубит)
            if (!P2.bShipDestroyed[i] && CheckF(P2.rcShips[i], P1))
                P2.bShipDestroyed[i] = true;
            // Проверим незакрашенные области вокруг корабля (сами нашли)
            if (!P2.bAreaShaded[i] && CheckF(GetShipArea(P2.rcShips[i]), P1))
                P2.bAreaShaded[i] = true;
        }
        // Проверим, а не завершена ли фигура?
        // Если N-корабль на поле остался один, то затеним область вокруг него,
        // если кораблей большего размера нет
        if (P2.bShipDestroyed[nCurrentShip] && !P2.bAreaShaded[nCurrentShip])
        {
            if (nCurrentShip == 0)
            {
                UpdateField(P1, P2, 0);
                for (int i=1; i<=2; i++)
                {
                    if (P2.bShipDestroyed[i])
                        UpdateField(P1, P2, i);
                }
            }
            else
            {
                boolean bFlag = true;
                for (int i=0; i<10; i++)
                {
                    if (!P2.bShipDestroyed[i])
                    {
                        bFlag = false;
                        break;
                    }
                    if (((nCurrentShip == 1 || nCurrentShip == 2) && i == 0) ||
                        ((nCurrentShip >= 3 && nCurrentShip <= 5) && i == 2) ||
                        (nCurrentShip >= 6 && i == 5))
                        break;
                }
                if (bFlag)
                {
                    UpdateField(P1, P2, nCurrentShip);
                    int nStart = 3;
                    int nEnd = 5;
                    if (nCurrentShip > 5)
                    {
                        nStart = 6;
                        nEnd = 9;
                    }
                    for (int i=nStart; i<=nEnd; i++)
                    {
                        if (P2.bShipDestroyed[i])
                            UpdateField(P1, P2, i);
                    }
                }
            }
        }
    }

    private void UpdateField(Data P1, Data P2, int nIndexShip)
    {
        Rect area = GetShipArea(P2.rcShips[nIndexShip]);
        for (int x=area.left; x<=area.right; x++)
        {
            for (int y = area.top; y <= area.bottom; y++)
            {
                if ((x >= 0 && x < 10) && (y >= 0 && y < 10))
                    P1.click[x][y] = true;
            }
        }
        P2.bAreaShaded[nIndexShip] = true;
    }

    private boolean CheckF(Rect rc, Data P1)
    {
        for(int x=rc.left; x<=rc.right; x++)
        {
            for(int y=rc.top; y<=rc.bottom; y++)
            {
                if (!((x >= 0 && x < 10) && (y >= 0 && y < 10)))
                    continue;
                if (!P1.click[x][y])
                    return false;
            }
        }
        return true;
    }

    private boolean CheckE(int x, int y)
    {
        IncreaseMoveCount(m_enemy, x, y);
        if (m_user.map[x][y] == 1)
        {
            DecreaseAliveCount(m_enemy, m_user, x, y);
            CheckEndGame();
            return true;
        }
        else
            return CheckEndGame();
    }

    private void OnEnemyAction()
    {
        while(true)
        {
            Random rnd = new Random();
            ArrayList<Point> moves = GetPossibleMoves();
            if (moves.size() != 0)
            {
                int i = rnd.nextInt(moves.size());
                Point pt = moves.get(i);
                if (!m_enemy.click[pt.x][pt.y])
                {
                    if (!CheckE(pt.x, pt.y))
                        return;
                }
            }
            else
            {
                int x = rnd.nextInt(10);
                int y = rnd.nextInt(10);
                if (!m_enemy.click[x][y])
                {
                    if (!CheckE(x, y))
                        break;
                }
            }
        }
    }

    public void NewGame()
    {// обновляем данные и поля противника заполняем
        m_nMoveCount = 0;
        m_user = new Data();
        m_enemy = new Data();
        Initialize(m_enemy);
        Initialize(m_user);
        invalidate();
    }

    private void Initialize(Data data)
    {
        while (true)
        {// Расставим корабли у противника
            int[][] field = new int[10][10]; //0 - пусто, 1 - корабль, 2-область вокруг
            if (RndShipPosition(field, data, 4, 0) &&
                RndShipPosition(field, data, 3, 1) &&
                RndShipPosition(field, data, 3, 2) &&
                RndShipPosition(field, data, 2, 3) &&
                RndShipPosition(field, data, 2, 4) &&
                RndShipPosition(field, data, 2, 5) &&
                RndShip1Position(field, data))
            {
                data.map = field;
                break;
            }
        }
    }

    private boolean RndShip1Position(int[][] field, Data data)
    {
        Random rnd = new Random();
        for (int i = 0; i < 4; i++)
        {// x,y принадлежат [0;10)
            int z = 0;
            while (z < 1000)
            {
                int x = rnd.nextInt(10);
                int y = rnd.nextInt(10);
                data.rcShips[6+i] = new Rect(x, y, x, y);
                if (Fill(field, data.rcShips[6+i]))
                    break;
                z++;
            }
            if (z >= 1000)
                return false;
        }
        return true;
    }

    private boolean RndShipPosition(int[][] field, Data data, int nCount, int nIndexShip)
    {
        Random rnd = new Random();
        int z = 0;
        while (z < 1000)
        {
            boolean bFlag = true;
            // x,y принадлежат [0;10)
            int x = rnd.nextInt(10);
            int y = rnd.nextInt(10);
            switch (rnd.nextInt(4))
            {// Направление
                case 0: //Вверх от y
                    if (y < nCount)
                    {
                        bFlag = false;
                        break;
                    }
                    data.rcShips[nIndexShip] = new Rect(x, y-nCount+1, x, y);
                    break;
                case 1: //Вниз от y
                    if (y > 10-nCount)
                    {
                        bFlag = false;
                        break;
                    }
                    data.rcShips[nIndexShip] = new Rect(x, y, x, y+nCount-1);
                    break;
                case 2: //Влево от x
                    if (x < nCount)
                    {
                        bFlag = false;
                        break;
                    }
                    data.rcShips[nIndexShip] = new Rect(x-nCount+1, y, x, y);
                    break;
                case 3: //Вправо от x
                    if (x > 10-nCount)
                    {
                        bFlag = false;
                        break;
                    }
                    data.rcShips[nIndexShip] = new Rect(x, y, x+nCount-1, y);
                    break;
            }
            if (bFlag && Fill(field, data.rcShips[nIndexShip]))
                break;
            else
                z++;
        }
        return z < 1000;
    }

    private Rect GetShipArea(Rect rc)
    {// Для точек генерирует область, в которой находится корабль
        return new Rect(rc.left-1, rc.top-1, rc.right+1, rc.bottom+1);
    }

    private boolean Fill(int[][] field, Rect rcShip)
    {// вернет false, если район нахождения кораблей пересекается
        Rect area = GetShipArea(rcShip);
        for (int x=area.left; x<=area.right; x++)
        {// Проверим пересекает ли район нахождения нашего корабля позицию другого корабля
            for (int y=area.top; y<=area.bottom; y++)
            {
                if((x >= 0 && x < 10) && (y >= 0 && y < 10) && field[x][y] == 1)
                    return false;
            }
        }
        for (int x=area.left; x<=area.right; x++)
        {// Продолжим заполнение проверочной таблицы
            for (int y=area.top; y<=area.bottom; y++)
            {
                if((x >= 0 && x < 10) && (y >= 0 && y < 10))
                {
                    int res = 2;
                    if ((x >= rcShip.left && x <= rcShip.right) &&
                            (y >= rcShip.top && y <= rcShip.bottom))
                        res = 1;
                    field[x][y] = res;
                }
            }
        }
        return true;
    }
}

public class MainActivity extends AppCompatActivity implements View.OnTouchListener
{
    private MainView m_view;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().hide();
        //getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
        // Инициализируем все необходимые данные
        m_view = new MainView(this);
        setContentView(m_view);
        m_view.setOnTouchListener(this);
        //Инициализируем игру
        m_view.NewGame();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event)
    {
        v.performClick();
        if (event.getAction() == MotionEvent.ACTION_DOWN)
        {
            if (m_view.CheckPos((int) event.getX(), (int) event.getY()))
                v.invalidate();
        }
        return true;
    }
}
